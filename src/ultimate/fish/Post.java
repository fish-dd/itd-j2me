//пока что не используется, и не знаю, будет ли
package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class Post {
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

    //только у поста
    String authorId;
    JSONObject originalPost;
    JSONObject poll;
    String wallRecipientId;
    String editedAt;
    int editedAgo; //кастомный параметр от прокси

    Post(JSONObject postJson) {
        id = postJson.getString("id");
        content = postJson.getString("content");
        spans = postJson.getArray("spans");
        likesCount = postJson.getInt("likesCount");
        commentsCount = postJson.getInt("commentsCount");
        repostsCount = postJson.getInt("repostsCount");
        viewsCount = postJson.getInt("viewsCount");
        authorId = postJson.getString("authorId");
        createdAt = postJson.getString("createdAt");
        createdAgo = postJson.getInt("createdAgo");
        author = postJson.getObject("author");
        attachments = postJson.getArray("attachments");
        isLiked = postJson.getBoolean("isLiked");
        isReposted = postJson.getBoolean("isReposted");
        isOwner = postJson.getBoolean("isOwner");
        isViewed = postJson.getBoolean("isViewed");
        originalPost = postJson.getObject("originalPost");
        dominantEmoji = postJson.getString("dominantEmoji");
        vs = postJson.getString("vs");
        poll = postJson.getNullableObject("poll");
        wallRecipientId = postJson.getString("wallRecipientId");
        editedAt = postJson.getString("editedAt");
        editedAgo = postJson.getInt("editedAgo");
    }
}