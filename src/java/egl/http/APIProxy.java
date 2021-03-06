
package egl.http;

import egl.Constants;
import egl.Eagle;
import egl.peer.Peer;
import egl.peer.Peers;
import egl.util.Logger;
import egl.util.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class APIProxy {
    public static final Set<String> NOT_FORWARDED_REQUESTS;

    private static final APIProxy instance = new APIProxy();

    static final boolean enableAPIProxy = Constants.isLightClient ||
            (Eagle.getBooleanProperty("egl.enableAPIProxy") && ! API.isOpenAPI);
    private static final int blacklistingPeriod = Eagle.getIntProperty("egl.apiProxyBlacklistingPeriod") / 1000;
    static final String forcedServerURL = Eagle.getStringProperty("egl.forceAPIProxyServerURL", "");

    private volatile String forcedPeerHost;
    private volatile List<String> peersHosts = Collections.emptyList();
    private volatile String mainPeerAnnouncedAddress;

    private final Map<String, Integer> blacklistedPeers = new ConcurrentHashMap<>();

    static {
        Set<String> requests = new HashSet<>();
        requests.add("getBlockchainStatus");
        requests.add("getState");

        final EnumSet<APITag> notForwardedTags = EnumSet.of(APITag.DEBUG, APITag.NETWORK);

        for (APIEnum api : APIEnum.values()) {
            APIServlet.APIRequestHandler handler = api.getHandler();
            if (handler.requireBlockchain() && !Collections.disjoint(handler.getAPITags(), notForwardedTags)) {
                requests.add(api.getName());
            }
        }

        NOT_FORWARDED_REQUESTS = Collections.unmodifiableSet(requests);
    }

    private static final Runnable peersUpdateThread = () -> {
        int curTime = Eagle.getEpochTime();
        instance.blacklistedPeers.entrySet().removeIf((entry) -> {
            if (entry.getValue() < curTime) {
                Logger.logDebugMessage("Unblacklisting API peer " + entry.getKey());
                return true;
            }
            return false;
        });
        List<String> currentPeersHosts = instance.peersHosts;
        if (currentPeersHosts != null) {
            for (String host : currentPeersHosts) {
                Peer peer = Peers.getPeer(host);
                if (peer != null) {
                    Peers.connectPeer(peer);
                }
            }
        }
    };

    static {
        if (!Constants.isOffline && enableAPIProxy) {
            ThreadPool.scheduleThread("APIProxyPeersUpdate", peersUpdateThread, 60);
        }
    }

    private APIProxy() {

    }

    public static void init() {}

    public static APIProxy getInstance() {
        return instance;
    }

    Peer getServingPeer(String requestType) {
        if (forcedPeerHost != null) {
            return Peers.getPeer(forcedPeerHost);
        }

        APIEnum requestAPI = APIEnum.fromName(requestType);
        if (!peersHosts.isEmpty()) {
            for (String host : peersHosts) {
                Peer peer = Peers.getPeer(host);
                if (peer != null && peer.isApiConnectable() && !peer.getDisabledAPIs().contains(requestAPI)) {
                    return peer;
                }
            }
        }

        List<Peer> connectablePeers = Peers.getPeers(p -> p.isApiConnectable() && !blacklistedPeers.containsKey(p.getHost()));
        if (connectablePeers.isEmpty()) {
            return null;
        }
        // subset of connectable peers that have at least one new API enabled, which was disabled for the
        // The first peer (element 0 of peersHosts) is chosen at random. Next peers are chosen randomly from a
        // previously chosen peers. In worst case the size of peersHosts will be the number of APIs
        Peer peer = getRandomAPIPeer(connectablePeers);
        if (peer == null) {
            return null;
        }

        Peer resultPeer = null;
        List<String> currentPeersHosts = new ArrayList<>();
        EnumSet<APIEnum> disabledAPIs = EnumSet.noneOf(APIEnum.class);
        currentPeersHosts.add(peer.getHost());
        mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
        if (!peer.getDisabledAPIs().contains(requestAPI)) {
            resultPeer = peer;
        }
        while (!disabledAPIs.isEmpty() && !connectablePeers.isEmpty()) {
            // remove all peers that do not introduce new enabled APIs
            connectablePeers.removeIf(p -> p.getDisabledAPIs().containsAll(disabledAPIs));
            peer = getRandomAPIPeer(connectablePeers);
            if (peer != null) {
                currentPeersHosts.add(peer.getHost());
                if (!peer.getDisabledAPIs().contains(requestAPI)) {
                    resultPeer = peer;
                }
                disabledAPIs.retainAll(peer.getDisabledAPIs());
            }
        }
        peersHosts = Collections.unmodifiableList(currentPeersHosts);
        Logger.logInfoMessage("Selected API peer " + resultPeer + " peer hosts selected " + currentPeersHosts);
        return resultPeer;
    }

    Peer setForcedPeer(Peer peer) {
        if (peer != null) {
            forcedPeerHost = peer.getHost();
            mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
            return peer;
        } else {
            forcedPeerHost = null;
            mainPeerAnnouncedAddress = null;
            return getServingPeer(null);
        }
    }

    String getMainPeerAnnouncedAddress() {
        // The first client request GetBlockchainState is handled by the server
        // Not by the proxy. In order to report a peer to the client we have
        // To select some initial peer.
        if (mainPeerAnnouncedAddress == null) {
            Peer peer = getServingPeer(null);
            if (peer != null) {
                mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
            }
        }
        return mainPeerAnnouncedAddress;
    }

    static boolean isActivated() {
        return Constants.isLightClient || (enableAPIProxy && Eagle.getBlockchainProcessor().isDownloading());
    }

    boolean blacklistHost(String host) {
        if (blacklistedPeers.size() > 1000) {
            Logger.logInfoMessage("Too many blacklisted peers");
            return false;
        }
        blacklistedPeers.put(host, Eagle.getEpochTime() + blacklistingPeriod);
        if (peersHosts.contains(host)) {
            peersHosts = Collections.emptyList();
            getServingPeer(null);
        }
        return true;
    }

    private Peer getRandomAPIPeer(List<Peer> peers) {
        if (peers.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(peers.size());
        return peers.remove(index);
    }
}
