package ultimate.fish;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class LikedCanvas extends ProfileCanvas {
    private static final String[] URL_PARTS = {ITD.API_URL + "/posts/user/", "/liked?limit=", "&cursor="};
    private static final String TITLE = "Лайки";

    private String userId;

    public LikedCanvas(ITD midlet, String profileUrl) {
        this.midlet = midlet;
        this.showSelection = !hasPointerEvents();
        this.profileUrl = profileUrl;

        setFullScreenMode(false);
        setTitle(TITLE);
        initFonts();
        setScreenSize();
        initIcons();

        loadProfile(profileUrl);

        initAvatarLoader();
        initMediaLoader();
        initPostLoader();

        initCommands();
    }


    JSONObject loadProfile(String url) {
        JSONObject profile = super.loadProfile(url);
        userId = profile.getString("id");
        return profile;
    }


    protected void loadPosts(String profileUrl, final int postsLimit) {
        String postsUrl = URL_PARTS[0] + userId + URL_PARTS[1] + postsLimit;
        if (cursor != null) {
            postsUrl += URL_PARTS[2] + cursor;
        }
        String postsResponse = ITD.getRequest(postsUrl, midlet.getRefreshToken(), true);
        JSONObject jsonData = JSON.getObject(postsResponse).getObject("data");
        if (jsonData.getObject("pagination").getString("nextCursor") != null) {
            cursor = jsonData.getObject("pagination").getString("nextCursor");
        }

        JSONArray posts = jsonData.getArray("posts");
        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            elements.addElement(posts.get(postIndex));
        }

        if (posts.isEmpty()) {
            ITD.log("Больше постов нет");
            isNoMorePosts = true;
        }
    }
}
