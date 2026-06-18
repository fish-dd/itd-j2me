package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class Repost {
    //общие параметры поста и репоста
    String id;
    String content;
    JSONArray spans;
    int likesCount;
    int commentsCount;
    int repostsCount;
    int viewsCount;
    String createdAt;
    int createdAgo; //кастомный параметр от прокси
    JSONObject author;
    JSONArray attachments;
    boolean isLiked;
    boolean isReposted;
    boolean isOwner;
    boolean isViewed;
    String dominantEmoji;
    String vs;

    //только у репоста
    boolean isDeleted;

    Repost(JSONObject postJson) {
        id = postJson.getString("id");
        content = postJson.getString("content");
        spans = postJson.getArray("spans");
        likesCount = postJson.getInt("likesCount");
        commentsCount = postJson.getInt("commentsCount");
        repostsCount = postJson.getInt("repostsCount");
        viewsCount = postJson.getInt("viewsCount");
        createdAt = postJson.getString("createdAt");
        createdAgo = postJson.getInt("createdAgo");
        author = postJson.getObject("author");
        attachments = postJson.getArray("attachments");
        isLiked = postJson.getBoolean("isLiked");
        isReposted = postJson.getBoolean("isReposted");
        isOwner = postJson.getBoolean("isOwner");
        isViewed = postJson.getBoolean("isViewed");
        dominantEmoji = postJson.getString("dominantEmoji");
        vs = postJson.getString("vs");
        isDeleted = postJson.getBoolean("isDeleted");
    }
}