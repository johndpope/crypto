
package egl.http;

import egl.Currency;
import egl.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyIds extends APIServlet.APIRequestHandler {

    static final GetCurrencyIds instance = new GetCurrencyIds();

    private GetCurrencyIds() {
        super(new APITag[] {APITag.MS}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray currencyIds = new JSONArray();
        try (DbIterator<Currency> currencies = Currency.getAllCurrencies(firstIndex, lastIndex)) {
            for (Currency currency : currencies) {
                currencyIds.add(Long.toUnsignedString(currency.getId()));
            }
        }
        JSONObject response = new JSONObject();
        response.put("currencyIds", currencyIds);
        return response;
    }

}
