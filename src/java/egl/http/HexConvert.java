
package egl.http;

import egl.util.Convert;
import egl.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class HexConvert extends APIServlet.APIRequestHandler {

    static final HexConvert instance = new HexConvert();

    private HexConvert() {
        super(new APITag[] {APITag.UTILS}, "string");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String string = Convert.emptyToNull(req.getParameter("string"));
        if (string == null) {
            return JSON.emptyJSON;
        }
        JSONObject response = new JSONObject();
        try {
            byte[] asHex = Convert.parseHexString(string);
            if (asHex.length > 0) {
                response.put("text", Convert.toString(asHex));
            }
        } catch (RuntimeException ignore) {}
        try {
            byte[] asText = Convert.toBytes(string);
            response.put("binary", Convert.toHexString(asText));
        } catch (RuntimeException ignore) {}
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
