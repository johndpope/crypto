
package egl.http;

import egl.PhasingPoll;
import egl.Transaction;
import egl.VoteWeighting;
import egl.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetCurrencyPhasedTransactions extends APIServlet.APIRequestHandler {
    static final GetCurrencyPhasedTransactions instance = new GetCurrencyPhasedTransactions();

    private GetCurrencyPhasedTransactions() {
        super(new APITag[]{APITag.MS, APITag.PHASING}, "currency", "account", "withoutWhitelist", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        long accountId = ParameterParser.getAccountId(req, false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean withoutWhitelist = "true".equalsIgnoreCase(req.getParameter("withoutWhitelist"));

        JSONArray transactions = new JSONArray();
        try (DbIterator<? extends Transaction> iterator = PhasingPoll.getHoldingPhasedTransactions(currencyId, VoteWeighting.VotingModel.CURRENCY,
                accountId, withoutWhitelist, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                transactions.add(JSONData.transaction(transaction));
            }
        }
        JSONObject response = new JSONObject();
        response.put("transactions", transactions);
        return response;
    }

}
