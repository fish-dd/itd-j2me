package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class Post {
    String id;
    String content;
    int likesCount;
    int commentsCount;
    int repostsCount;
    int viewsCount;
    String authorId;
    int createdAgo;
    JSONObject author;
    JSONArray attachments;
    boolean isLiked;
    boolean isReposted;
    boolean isOwner;
    boolean isViewed;
    JSONObject originalPost;
    String dominantEmoji;

    Post(JSONObject postJson) {
        id = postJson.getString("id");
        content = postJson.getString("content");
        likesCount = postJson.getInt("likesCount");
        commentsCount = postJson.getInt("commentsCount");
        repostsCount = postJson.getInt("repostsCount");
        viewsCount = postJson.getInt("viewsCount");
        authorId = postJson.getString("authorId");
        createdAgo = postJson.getInt("createdAt");
        author = postJson.getObject("author");
        attachments = postJson.getArray("attachments");
        isLiked = postJson.getBoolean("isLiked");
        isReposted = postJson.getBoolean("isReposted");
        isOwner = postJson.getBoolean("isOwner");
        isViewed = postJson.getBoolean("isViewed");
        originalPost = postJson.getObject("originalPost");
        dominantEmoji = postJson.getString("dominantEmoji");
    }
}