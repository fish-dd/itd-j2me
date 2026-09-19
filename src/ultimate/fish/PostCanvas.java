package ultimate.fish;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.*;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class PostCanvas extends FeedCanvas {
    boolean kolbasa = true;
    private static final String TITLE = "Пост";
    private static final String REPLIES_LOADER_ID = "repliesLoader";

    final String postId;
    JSONObject post;

    final Vector commentsMediasQueue = new Vector();
    Hashtable commentsMedias = new Hashtable();
    int commentMediaWidth;
    int replyMediaWidth;

    Hashtable elementsHeights = postsHeights;
    Hashtable commentsStrings = new Hashtable();

    private int replyPadding;
    private int loadRepliesHeight;

    final FeedCanvas parentScreen;

    Thread commentLoader;
    final Object commentLoadNotifier = new Object();
    boolean areCommentsRequested = false;
    String cursor = null;

    Vector repliesRequests = new Vector();

    Hashtable repliesHitboxes;

    int totalComments = -1;
    int currentComments = 0;

    PostCanvas(ITD midlet, JSONObject post, FeedCanvas parentScreen) {
        super();
        this.midlet = midlet;
        this.post = post;
        this.postId = post.getString("id");
        this.parentScreen = parentScreen;

        setFullScreenMode(true);
        setTitle(TITLE);
        initFonts();
        setScreenSize();
        initIcons();

        avatars = parentScreen.avatars;
        initAvatarLoader();
        medias = parentScreen.medias;
        mediasQueue = parentScreen.mediasQueue;
        initMediaLoader();

//        loadPost();
        elements.addElement(post);

        initCommentLoader();

        initCommands();

        ITD.log("пост стартовал");
    }


    void setScreenSize() {
        super.setScreenSize();

        replyPadding = Math.max(screenWidth / 10, 20);
        loadRepliesHeight = lineHeight + PADDING*4;
        elementsHeights.put(REPLIES_LOADER_ID, new Integer(loadRepliesHeight));

        commentMediaWidth = screenWidth - PADDING*2;
        replyMediaWidth = commentMediaWidth - replyPadding;
    }


    JSONObject loadPost() {
        String url = ITD.API_URL + "/posts/" + postId;

        String response = ITD.getRequest(url, midlet.getRefreshToken(), true);
        post = JSON.getObject(response);

        return post;
    }


    void loadComments(int limit) {
        String url = ITD.API_URL + "/posts/" + postId + "/comments?sort=popular&limit=" + limit;
        if (cursor != null) url += "&cursor=" + cursor;

        String response = ITD.getRequest(url, midlet.getRefreshToken(), true);
        JSONObject jsonData = JSON.getObject(response).getObject("data");

        if (jsonData.getString("nextCursor") != null) cursor = jsonData.getString("nextCursor");

        totalComments = jsonData.getInt("total");
        JSONArray comments = jsonData.getArray("comments");

        for (int commentIndex = 0; commentIndex < comments.size(); commentIndex++) {
            currentComments++;

            JSONObject comment = comments.getObject(commentIndex);
            elements.addElement(comment);

            JSONArray replies = comment.getArray("replies");
            for (int replyIndex = 0; replyIndex < replies.size(); replyIndex++) {
                JSONObject reply = replies.getObject(replyIndex);
                reply.put("parent", comment.getString("id"));
                elements.addElement(reply);
            }

            int loaded = replies.size();
            if (comment.getInt("repliesCount") > loaded) {
                int rest = comment.getInt("repliesCount") - loaded;
                JSONObject repliesLoader = new JSONObject();
                repliesLoader.put("id", REPLIES_LOADER_ID);
                repliesLoader.put("parent", comment.getString("id"));
                repliesLoader.put("page", 1);
                repliesLoader.put("loaded", loaded);
                repliesLoader.put("rest", rest);
                elements.addElement(repliesLoader);
            }
        }
    }


    void initMediaLoader() {
        ITD.log("медиа поток");
        mediaLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    while (commentsMediasQueue.isEmpty()) {
                        synchronized (commentsMediasQueue) {
                            try {
                                commentsMediasQueue.wait(); //пик шизы
                            } catch (Exception e) { ITD.log(String.valueOf(e)); } //ожидание реальность
                        }
                    }

                    ITD.log("Запрос на медиа " + commentsMediasQueue);
                    Object[] mediaRequest = (Object[]) commentsMediasQueue.elementAt(0);
                    String fileName = (String) mediaRequest[0];
                    boolean isReply = ((Boolean) mediaRequest[1]).booleanValue();
                    String commentId = (String) mediaRequest[2];

                    int mediaWidth = isReply ? replyMediaWidth : commentMediaWidth;

                    Image media;
                    try {
                        String mediaUrl = ITD.URL + "/media/" + fileName + "?width=" + mediaWidth;
                        InputStream mediaRaw = ITD.rawGetRequest(mediaUrl);
                        media = Image.createImage(mediaRaw);

                        int mediaHeight = ((Integer) elementsHeights.get(fileName)).intValue();
                        if (media.getHeight() != mediaHeight) {
                            ITD.log("НЕСОСТЫКОВКА " + media.getHeight() + " " + mediaHeight);
                            elementsHeights.put(fileName, new Integer(media.getHeight()));

                            Integer newPostHeight = new Integer(((Integer) elementsHeights.get(commentId)).intValue() + media.getHeight() - mediaHeight);
                            elementsHeights.put(commentId, newPostHeight);
                        }
                    }
                    catch (Exception e) {
                        ITD.log("Ошибка создания медиа " + e);
                        media = Image.createImage(mediaWidth, 100);
                    }

                    commentsMedias.put(fileName, media);
                    commentsMediasQueue.removeElementAt(0);

                    repaint();
                }
            }
        }, "mediaLoader");

        ITD.log("медиа поток запуск");
        mediaLoader.start();
    }


    void initCommentLoader() {
        ITD.log("коммент поток");
        commentLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    synchronized (commentLoadNotifier) {
                        try {
                            commentLoadNotifier.wait();
                        } catch (Exception e) { ITD.log(String.valueOf(e)); }
                    }

                    loadComments(ITD.COMMENTS_LIMIT);
                    areCommentsRequested = false;
                    repaint();
                }
            }
        }, "commentLoader");

        ITD.log("коммент поток запуск");
        commentLoader.start();
    }


    void requestComments() {
        if (!areCommentsRequested) {
            areCommentsRequested = true;
            synchronized (commentLoadNotifier) {
                commentLoadNotifier.notify();
            }
        }
    }
    
    
    protected void paint(Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        //чтобы почистить очередь областей нажатия
        if (hasPointerEvents()) {
            likesHitboxes = new Hashtable();
            commentsHitboxes = new Hashtable();
            repostsHitboxes = new Hashtable();
            repliesHitboxes = new Hashtable();
        }

        if (showSelection) {
            if (selectedIndex == 0) {
                addNontouchCmds();
                removeCommand(midlet.replyCmd);
            }
            else if (getSel().get("id").equals(REPLIES_LOADER_ID)) {
                removeNontouchCmds();
                addCommand(midlet.loadCmd);
            }
            else {
                addNontouchCmds();
                addCommand(midlet.replyCmd);
            }
        }

        elementsHeightTemp = 0;

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            JSONObject element = (JSONObject) elements.elementAt(elementIndex);

            if (element.has("hidden") && element.getBoolean("hidden")) continue;

            boolean isSelected = selectedIndex == elementIndex;
            if (isSelected) selectedY = currentY;

            if (elementIndex == 0) {
                drawPost(g, currentY, post, isSelected);
            }
            else if (element.getString("id").equals(REPLIES_LOADER_ID)) {
                drawRepliesLoader(g, currentY, element, isSelected);
            }
            else {
                drawComment(g, currentY, element, isSelected);
            }

            // Сдвигаем курсор рисования вниз
            currentY += ((Integer) elementsHeights.get(element.getString("id"))).intValue();
        }

        if (totalComments == 0) {
            String emptyLabel = "Нет комментариев";
            g.setColor(COLOR_SEL);
            g.drawString(
                    emptyLabel,
                    screenWidth / 2,
                    currentY + 1,
                    Graphics.TOP | Graphics.HCENTER
            );
        }

        elementsHeight = elementsHeightTemp;

        if ((scrollY + screenHeight >= elementsHeight) && (currentComments != totalComments)) requestComments();

        if (arePostsRequested) drawLoadNotify(g, "Прогрузка постов...");
        else if (!repliesRequests.isEmpty()) drawLoadNotify(g, "Прогрузка ответов...");
    }


    int getCommentHeight(JSONObject comment) {
        String id = comment.getString("id");

        if (elementsHeights.containsKey(id)) {
            return ((Integer) elementsHeights.get(id)).intValue();
        }

        int commentHeight = calcCommentHeight(comment);
        elementsHeights.put(id, new Integer(commentHeight));
        return commentHeight;
    }


//    int calcCommentHeight(JSONObject comment) {
//        int linesCount = ((String[]) commentsStrings.get(comment.getString("id"))).length;
//        int height = fontPlain.getHeight()*(linesCount - 1) + PADDING*3 + avatarSize;
//        return Math.max(height, avatarSize + PADDING*2);
//    }


    //функция взята из feedcanvas и переделана под комменты
    protected int calcCommentHeight(JSONObject comment) {
        String[] content = (String[]) commentsStrings.get(comment.getString("id"));
        int linesCount = content.length;

        int height = fontPlain.getHeight() * (linesCount - 1) + PADDING*3 + avatarSize;
        height = Math.max(height, avatarSize + PADDING*2);

        JSONArray medias = comment.getArray("attachments", null);

        if (!medias.isEmpty()) {
            for (int mediaIndex = 0; mediaIndex < medias.size(); mediaIndex++) {
                JSONObject mediaInfo = medias.getObject(mediaIndex);

                int mediaHeight = getMediaHeight(mediaInfo, postMediaWidth);
                height += mediaHeight + PADDING;

                //сохранение высоты медиа в словарь
                String fileName = ITD.getFileName(mediaInfo.getString("url"));
                elementsHeights.put(fileName, new Integer(mediaHeight));
            }
        }

        ITD.log("Высота коммента: " + height);
        return height;
    }


    void drawComment(Graphics g, int currentY, JSONObject comment, boolean isSelected) {
        final String id = comment.getString("id");

        boolean isReply = comment.has("replyTo");
        int commentWidth = screenWidth - PADDING*2;
        if (isReply) commentWidth -= replyPadding;

        String[] content;
        if (commentsStrings.contains(id)) {
            content = (String[]) commentsStrings.get(id);
        }
        else {
            String contentStr = comment.getString("content");
            JSONArray medias = comment.getArray("attachments");
            for (int mediaIndex = 0; mediaIndex < medias.size(); mediaIndex++) {
                String type = ((JSONObject) medias.get(mediaIndex)).getString("type");
                if (type.equals("audio")) {
                    contentStr = "[Аудио] " + contentStr;
                }
                else if (type.equals("video")) { //хз можно ли их в комменты отправлять, но пусть будет
                    contentStr = "[Видео] " + contentStr;
                }
            }
            if (isReply) {
                String recipientName = comment.getObject("replyTo").getString("displayName");
                contentStr = "@" + recipientName + ", " + contentStr;
            }
            content = split(
                    contentStr,
                    fontPlain,
                    commentWidth - PADDING - avatarSize,
                    commentWidth
            );
            commentsStrings.put(id, content);
        }

        int commentHeight = getCommentHeight(comment);
        elementsHeightTemp += commentHeight;

        // Оптимизация: Рисуем, только если коммент попадает в экран
        if (currentY + commentHeight > 0 && currentY < screenHeight) {
            // Рисуем фон выделения, если коммент выбран курсором
            if (isSelected && showSelection) {
                g.setColor(COLOR_SEL);
                if (isReply) {
                    g.fillRect(replyPadding, currentY, screenWidth - replyPadding, commentHeight);
                }
                else {
                    g.fillRect(0, currentY, screenWidth, commentHeight);
                }
            }
            //чтобы после перехода с сенсора на кнопки выделение было на комменте посреди экрана:
            else if (!showSelection && -currentY + screenHeight/2 <= commentHeight && currentY <= screenHeight/2){
                selectedIndex = elements.indexOf(comment);
            }

            if (hasPointerEvents()) {
                int[] h = new int[]{
                    0,
                    currentY,
                    screenWidth,
                    currentY + commentHeight
                };
                repliesHitboxes.put(h, comment);
                if (TOUCH_DEBUG) {
                    g.setColor(0xFF0000);
                    g.drawRect(h[0], h[1], h[2] - h[0], h[3] - h[1]);
                }
            }

            //содержимое коммента
            int padding = PADDING;
            if (isReply) padding += replyPadding;

            //аватарка
            String emoji = comment.getObject("author").getString("avatar");
            String emojiId = getEmojiId(emoji);
            if (avatars.containsKey(emojiId)) {
                if (avatars.get(emojiId) != requestMarker) {
                    Image avatar = (Image) avatars.get(emojiId);
                    g.drawImage(avatar, padding, currentY + PADDING, 0);
                }
            }
            else {
                avatars.put(emojiId, requestMarker); //маркер реквеста
                synchronized (avatarsQueue) {
                    avatarsQueue.addElement(emojiId);
                    avatarsQueue.notify();
                }
            }

            //ник
            g.setColor(COLOR_TEXT);
            g.setFont(fontBold);
            String displayName = comment.getObject("author").getString("displayName");
            int metadataY = currentY + PADDING + 2;
            g.drawString(displayName, padding + avatarSize + PADDING, metadataY, 0);

            //лайки
            g.setColor(COLOR_TEXT);
            g.setFont(fontBold);
            boolean isLiked = comment.getBoolean("isLiked");
            int likesCount = comment.getInt("likesCount");
            String likesCountStr = String.valueOf(likesCount);
            int likesCountWidth = strWidth(likesCountStr, fontBold);
            int likesWidth = iconSize + PADDING + likesCountWidth;
            g.drawImage(
                    isLiked ? likeFillIcon : likeIcon,
                    screenWidth - PADDING - likesWidth,
                    metadataY,
                    0
            );
            g.drawString(
                    likesCountStr,
                    screenWidth - PADDING - likesCountWidth,
                    metadataY,
                    0
            );
            if (hasPointerEvents()) {
                int[] h = new int[] {
                    screenWidth - PADDING - likesWidth,
                    metadataY,
                    screenWidth - PADDING,
                    metadataY + iconSize
                };
                likesHitboxes.put(h, comment);
                if (TOUCH_DEBUG) {
                    g.setColor(0xFF0000);
                    g.drawRect(h[0], h[1], h[2]-h[0], h[3]-h[1]);
                }
            }

            //текст
            g.setColor(COLOR_TEXT);
            g.setFont(fontPlain);
            int contentY = currentY + PADDING*2 + avatarSize - lineHeight;
            if (content.length == 1) contentY -= 2; //чтобы если одна строка текст повыше рисовался
            for (int lineIndex = 0; lineIndex < content.length; lineIndex++) {
                g.drawString(
                        content[lineIndex],
                        lineIndex == 0 ? padding + avatarSize + PADDING : padding,
                        contentY + lineHeight*lineIndex,
                        Graphics.TOP | Graphics.LEFT
                );
            }

            //картинки
            JSONArray attachments = comment.getArray("attachments");
            if (!attachments.isEmpty()) {
                String commentId = comment.getString("id");
                int mediaY = currentY + Math.max(lineHeight * (content.length - 1)
                        + PADDING*3 + avatarSize, avatarSize + PADDING*2);

                for (int mediaIndex = 0; mediaIndex < attachments.size(); mediaIndex++) {
                    JSONObject mediaInfo = attachments.getObject(mediaIndex);

                    if (!mediaInfo.getString("type").equals("image")) continue;

                    String url = mediaInfo.getString("url");
                    String fileName = ITD.getFileName(url);

                    int mediaHeight = ((Integer) elementsHeights.get(fileName)).intValue();

                    if (commentsMedias.containsKey(fileName) && commentsMedias.get(fileName) != requestMarker) {
                        Image media = (Image) commentsMedias.get(fileName);
                        g.drawImage(
                                media,
                                padding,
                                mediaY,
                                0
                        );
                    }
                    else {
                        g.setColor(COLOR_LOADING);
                        g.fillRect(
                                padding,
                                mediaY,
                                isReply ? replyMediaWidth : commentMediaWidth,
                                mediaHeight
                        );

                        if (!commentsMedias.containsKey(fileName)) {
                            commentsMedias.put(fileName, requestMarker); //маркер реквеста
                            Object[] mediaRequest = new Object[]{fileName, ITD.bool(isReply), commentId};
                            commentsMediasQueue.addElement(mediaRequest);

                            synchronized (commentsMediasQueue) {
                                commentsMediasQueue.notify();
                            }
                        }
                    }

                    mediaY += mediaHeight + PADDING;
                }
            }

            //разделительные линии
            g.setColor(COLOR_SEL);
            g.drawLine(isReply ? replyPadding : 0, currentY + commentHeight - 1, screenWidth - 1, currentY + commentHeight - 1);
            g.drawLine(isReply ? replyPadding : 0, currentY - 1, screenWidth - 1, currentY - 1);
        }
    }


    private void drawRepliesLoader(Graphics g, int currentY, JSONObject element, boolean isSelected) {
        elementsHeightTemp += loadRepliesHeight;

        if (currentY + loadRepliesHeight > 0 && currentY < screenHeight) {
            if (isSelected && showSelection) {
                g.setColor(COLOR_SEL);
                g.fillRect(replyPadding, currentY, screenWidth - replyPadding, loadRepliesHeight);
            }
            //чтобы после перехода с сенсора на кнопки выделение было на комменте посреди экрана:
            else if (!showSelection && -currentY + screenHeight/2 <= loadRepliesHeight && currentY <= screenHeight/2){
                selectedIndex = elements.indexOf(element);
            }

            if (hasPointerEvents() && !element.getBoolean("hidden", false)) {
                int[] h = new int[]{
                        0,
                        currentY,
                        screenWidth,
                        currentY + loadRepliesHeight
                };
                repliesHitboxes.put(h, element);
                if (TOUCH_DEBUG) {
                    g.setColor(0xFF0000);
                    g.drawRect(h[0], h[1], h[2] - h[0], h[3] - h[1]);
                }
            }

            //надпись
            int rest = element.getInt("rest");
            g.setColor(COLOR_TEXT);
            g.setFont(fontPlain);
            g.drawString(
                    "Ещё " + rest + " " + countCase(rest, new String[]{"ответ", "ответа", "ответов"}),
                    replyPadding + PADDING*2,
                    currentY + (loadRepliesHeight - lineHeight) / 2,
                    Graphics.TOP | Graphics.LEFT
            );

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(
                    0,
                    currentY + loadRepliesHeight - 1,
                    screenWidth - 1,
                    currentY + loadRepliesHeight - 1
            );
        }
    }


    //попросил нейронку вырезать слайсер из мпграма, но похоже на нейрослоп
    public static String[] split(String text, Font font, int firstMaxWidth, int restMaxWidth) {
        if (text == null || text.length() == 0) {
            return new String[0];
        }

        Vector lines = new Vector();
        int lineIndex = 0;
        int len = text.length();
        int start = 0;
        int currentWidth = 0;
        int lastSpaceIndex = -1;

        for (int i = 0; i < len; i++) {
            int maxWidth = restMaxWidth;
            if (lineIndex == 0) maxWidth = firstMaxWidth;

            char c = text.charAt(i);

            // 1. Обработка принудительного переноса строки (\n)
            if (c == '\n') {
                lineIndex++;
                lines.addElement(text.substring(start, i));
                start = i + 1;
                currentWidth = 0;
                lastSpaceIndex = -1;
                continue;
            }

            int charWidth = font.charWidth(c);

            // 2. Если добавление символа превысит ширину экрана
            if (currentWidth + charWidth > maxWidth) {
                // Пытаемся разорвать по последнему пробелу
                if (lastSpaceIndex != -1 && lastSpaceIndex > start) {
                    lineIndex++;
                    lines.addElement(text.substring(start, lastSpaceIndex));
                    start = lastSpaceIndex + 1; // Следующая строка начинается после пробела
                    i = start - 1; // "Откатываем" цикл назад к началу нового слова
                } else {
                    // Пробелов не было (очень длинное слово), режем жестко по букве
                    lineIndex++;
                    lines.addElement(text.substring(start, i));
                    start = i;
                    i--; // "Откатываем" чтобы текущий символ попал в следующую строку
                }
                currentWidth = 0;
                lastSpaceIndex = -1;
            } else {
                // Символ влезает, просто учитываем его
                currentWidth += charWidth;
                if (c == ' ') {
                    lastSpaceIndex = i;
                }
            }
        }

        // 3. Добавляем "хвост" (все что осталось после последнего переноса)
        if (start < len) {
            lineIndex++; //бесполезно, но пусть будет, я перфекционист (наверное по качеству кода и не скажешь)
            lines.addElement(text.substring(start));
        }

        // Конвертация Vector в массив String[] (для скорости чтения в paint)
        String[] result = new String[lines.size()];
        lines.copyInto(result);
        return result;
    }


    void likePost() { like(); } //alias по сути


    void like() {
        like((JSONObject) elements.elementAt(selectedIndex));
    }


    void like(final JSONObject element) {
        if (element.has("replies")) {
            likeComment(element);
        }
        else {
            likePost(element);
        }
    }


    void likeComment(final JSONObject comment) {
        boolean isLiked = comment.getBoolean("isLiked");
        isLiked = !isLiked;
        comment.put("isLiked", isLiked);

        int likesCount = comment.getInt("likesCount");
        comment.put("likesCount", isLiked ? likesCount + 1 : likesCount - 1);

        repaint();

        final boolean fIsLiked = isLiked;
        final String url = ITD.API_URL + "/comments/" + comment.getString("id") + "/like";
        new Thread(new Runnable() {
            public void run() {
                if (fIsLiked) {
                    ITD.postRequest(url, new byte[]{}, midlet.getRefreshToken());
                }
                else {
                    ITD.deleteRequest(url, new byte[]{}, midlet.getRefreshToken());
                }
            }
        }, "likeComment").start();
    }


    void commentPost() {
        commentPost(post);
    }


    void repostPost() {
        repostPost(post);
    }


    void reply(JSONObject comment) {
        int index = elements.indexOf(comment);
        String commentId;
        if (comment.has("parent")) {
            commentId = comment.getString("parent");
        }
        else {
            commentId = comment.getString("id");
        }
        String recipientId = comment.getObject("author").getString("id");
        String name = comment.getObject("author").getString("displayName");
        midlet.initWriter(Writer.REPLY, recipientId, commentId, name, this, index);
    }


    void reply() {
        reply((JSONObject) elements.elementAt(selectedIndex));
    }


    void loadReplies(final JSONObject loadMore) {
        final String[] urlParts = {ITD.API_URL + "/comments/", "/replies?limit=", "&page="};

        Runnable repliesRunnable = new Runnable() {
            public void run() {
                Object requestHolder = new Object();
                repliesRequests.addElement(requestHolder);
                ITD.log(repliesRequests);
                loadMore.put("hidden", true);
                repaint();

                int page = loadMore.getInt("page");
                int loaded = loadMore.getInt("loaded");
                String parent = loadMore.getString("parent");

                String url = urlParts[0] + parent + urlParts[1] + ITD.REPLIES_LIMIT;

                if (page == 1) {
                    loaded = 0;
                    for (int index = 1; index < elements.size(); index++) {
                        JSONObject element = (JSONObject) elements.elementAt(index);
                        if (element.has("replyTo") && element.getString("parent", "").equals(parent)
                                /* && !element.getBoolean("new", false) */) {
                            elements.removeElementAt(index);
                        }
                    }
                }
                else url += urlParts[2] + page;

                String response = ITD.getRequest(url, midlet.getRefreshToken());
                JSONObject jsonData = JSON.getObject(response).getObject("data");

                JSONArray replies = jsonData.getArray("replies");
                for (int replyIndex = 0; replyIndex < replies.size(); replyIndex++) {
                    JSONObject reply = replies.getObject(replyIndex);
                    reply.put("parent", parent);
                    elements.insertElementAt(reply, elements.indexOf(loadMore));
                }

                if (jsonData.getObject("pagination").getBoolean("hasMore")) {
                    loadMore.put("page", ++page);
                    loadMore.put("loaded", loaded += ITD.REPLIES_LIMIT);
                    loadMore.put("rest", jsonData.getObject("pagination").getInt("total") - loaded);

                    loadMore.put("hidden", false);
                }
                else {
                    elements.removeElement(loadMore);
                }

                repliesRequests.removeElement(requestHolder);
                repaint();
            }
        };
        Thread repliesLoader = new Thread(repliesRunnable, "repliesLoader");
        repliesLoader.start();
    }


    void loadReplies() {
        loadReplies((JSONObject) elements.elementAt(selectedIndex));
    }


    void insertComment(JSONObject comment) {
        elements.insertElementAt(comment, 1);
    }


    void insertReply(JSONObject reply, int index) {
        elements.insertElementAt(reply, index);
    }


    boolean likesHbCheck(int x, int y) {
        Enumeration likeHbEnumKeys = likesHitboxes.keys();
        while (likeHbEnumKeys.hasMoreElements()) {
            int[] c /*coords*/ = (int[]) likeHbEnumKeys.nextElement();
            if (c[0] <= x && x <= c[2] && c[1] <= y && y <= c[3]) {
                ITD.log("Отправка лайка");
                like((JSONObject) likesHitboxes.get(c));
                return true;
            }
        }
        return false;
    }


    boolean repliesHbCheck(int x, int y) {
        Enumeration replyHbEnumKeys = repliesHitboxes.keys();
        while (replyHbEnumKeys.hasMoreElements()) {
            int[] c /*coords*/ = (int[]) replyHbEnumKeys.nextElement();
            if (c[0] <= x && x <= c[2] && c[1] <= y && y <= c[3]) {
                ITD.log("Открытие окна ответа");
                JSONObject element = (JSONObject) repliesHitboxes.get(c);
                if (element.getString("id").equals(REPLIES_LOADER_ID)) {
                    loadReplies(element);
                }
                else {
                    reply(element);
                }
                return true;
            }
        }
        return false;
    }


    protected void hitBoxesCheck(int x, int y) {
        if (likesHbCheck(x, y)) return;
        if (commentsHbCheck(x, y)) return;
        if (repostsHbCheck(x, y)) return;
        if (repliesHbCheck(x, y)) return;
    }


    protected void addNontouchCmds() {
        addCommand(midlet.likeCmd);
        addCommand(midlet.commentCmd);
        addCommand(midlet.repostCmd);
    }


    protected void removeNontouchCmds() {
        removeCommand(midlet.likeCmd);
        removeCommand(midlet.commentCmd);
        removeCommand(midlet.repostCmd);
        removeCommand(midlet.replyCmd);
        removeCommand(midlet.loadCmd);
    }


    public void stopFeed() {
        commentLoader.interrupt();
        ITD.log("commentLoader");
        avatarLoader.interrupt();
        ITD.log("avatarLoader");
        mediaLoader.interrupt();
        ITD.log("mediaLoader");
        scrollThread.interrupt();
        ITD.log("scrollLoader");
        setCommandListener(null);
        removeCommand(midlet.backToMenuCmd);
        removeNontouchCmds();
    }
}
