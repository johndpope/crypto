
package egl.http;

import egl.Poll;
import egl.db.DbIterator;
import egl.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SearchPolls extends APIServlet.APIRequestHandler {

    static final SearchPolls instance = new SearchPolls();

    private SearchPolls() {
        super(new APITag[] {APITag.VS, APITag.SEARCH}, "query", "firstIndex", "lastIndex", "includeFinished");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String query = Convert.nullToEmpty(req.getParameter("query"));
        if (query.isEmpty()) {
            return JSONResponses.missing("query");
        }
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeFinished = "true".equalsIgnoreCase(req.getParameter("includeFinished"));

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        try (DbIterator<Poll> polls = Poll.searchPolls(query, includeFinished, firstIndex, lastIndex)) {
            while (polls.hasNext()) {
                jsonArray.add(JSONData.poll(polls.next()));
            }
        }
        response.put("polls", jsonArray);
        return response;
    }

}
