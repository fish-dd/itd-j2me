package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class ProfileCanvas extends FeedCanvas {
    private JSONObject profile;

    public ProfileCanvas(JSONObject profile, JSONArray posts, ITD midlet) {
        super(posts, midlet);
    }


}