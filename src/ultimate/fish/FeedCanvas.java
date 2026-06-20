package ultimate.fish;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.*;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class FeedCanvas extends ScrollableCanvas {
    static final String[] URL_PARTS = {ITD.API_URL + "/posts?limit=", "&tab=popular", "&cursor="};

    ITD midlet;

    Hashtable postsStrings = new Hashtable(); //словарь из массивов со строками постов
    Hashtable repostsStrings = new Hashtable();
    Hashtable postsHeights = new Hashtable(); //высоты постов и отдельных медиа постов
    Hashtable repostsMediaHeights = new Hashtable(); //высоты репостов и отдельных медиа репостов
    int elementsHeightTemp;

    Vector viewedPosts = new Vector();

    Hashtable avatars = new Hashtable();
    final Vector avatarsQueue = new Vector();
    Thread avatarLoader;

    Hashtable medias = new Hashtable();
    final Vector mediasQueue = new Vector();
    Thread mediaLoader;
    final Object requestMarker = new Object();

    final Object postLoadNotifier = new Object();
    Thread postLoader;
    int cursor = 0;
    boolean arePostsRequested = false;

    int avatarSize;
    int iconSize;
    int minPostHeight;

    // Шрифты
    Font fontBold;
    Font fontPlain;
    int lineHeight;

    // Константы для верстки
    static final int PADDING = 5;
    static final int COLOR_BG = 0x000000;
    static final int COLOR_TEXT = 0xE4E6E8;
    static final int COLOR_SEL = 0x242424;
    static final int COLOR_LOADING = 0x323232;
    static final int COLOR_POST_REQUEST_NOTIFY = 0x58BED1; //второй рандом цвет из пипетки кста, первый был #FC64C1
    static final int COLOR_NUKSTA = 0x4FC3F7;
    static final float MAX_MEDIA_RATIO = 3f;

    int postMediaWidth;
    int repostMediaWidth;

    //иконки
    //google material symbols, Apache License, Version 2.0
    //size 16, weight 400, grade -25, optical size 20, #E4E6E8
    Image likeIcon;
    Image likeFillIcon;
    Image commentIcon;
    Image viewIcon;
    Image repostIcon;
    Image verifiedIcon;
    Image moreIcon;

    int headerHeight = 0;

    static final int POST = 0;
    static final int REPOST = 1;
    static final int BANNER = 2;

    Hashtable likesHitboxes;
    Hashtable repostsHitboxes;


    public FeedCanvas(ITD midlet) {
        super();
        this.midlet = midlet;

        setFullScreenMode(false);
        initFonts();
        setScreenSize();
        initIcons();

        initAvatarLoader();
        initMediaLoader();
        initPostLoader();

        loadPosts(ITD.POSTS_LIMIT);

        initCommands();

        ITD.log("фид стартовал");
    }


    public FeedCanvas() {}


    void setScreenSize() {
        screenWidth = getWidth();
        screenHeight = getHeight();

        postMediaWidth = screenWidth - PADDING*2;
        repostMediaWidth = screenWidth - PADDING*4 - 2;

        iconSize = midlet.iconSize;
        avatarSize = midlet.avatarSize;
        minPostHeight = avatarSize + PADDING*2;
    }


    void initFonts() {
        fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        lineHeight = fontPlain.getHeight();
        ITD.log("Высота строки текста: " + lineHeight);
    }


    void initIcons() {
        try {
            likeIcon = midlet.getIcon("like");
            likeFillIcon = midlet.getIcon("like_fill");
            commentIcon = midlet.getIcon("comment");
            viewIcon = midlet.getIcon("view");
            repostIcon = midlet.getIcon("repost");
            verifiedIcon = midlet.getIcon("verified");
            moreIcon = midlet.getIcon("three_dots");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
    }


    void initCommands() {
        setCommandListener(midlet.feedCmdListener);
        addCommand(midlet.backToMenuCmd);
        if (showSelection) {
            addNontouchCmds();
        }
    }


    protected int getElementHeight(Object object) {
        if (object instanceof JSONObject) {
            JSONObject post = (JSONObject) object;
            String postId = post.getString("id");

            if (postsHeights.containsKey(postId)) {
                return ((Integer) postsHeights.get(postId)).intValue();
            }

            int postHeight = calcPostHeight(post);
            postsHeights.put(postId, new Integer(postHeight));
            return postHeight;
        }
        throw new IllegalArgumentException();
    }


    int calcPostHeight(JSONObject post) {
        //разбиение текста поста по строкам
        String[] content = split(post.getString("content"), fontPlain, screenWidth - PADDING*2);

        //расчёт высоты поста с учётом только главного текста
        int postHeight = Math.max(PADDING*3 + avatarSize + lineHeight*content.length + iconSize, minPostHeight);
        if (content.length != 0) postHeight += PADDING; //отступ после текста

        JSONArray medias = post.getArray("attachments");
        JSONObject originalPost = post.getObject("originalPost");

        //расчёт высоты контента поста (при наличии)
        //если в посте есть фото/медиа
        if (!medias.isEmpty()) {
            for (int mediaIndex = 0; mediaIndex < medias.size(); mediaIndex++) {
                JSONObject mediaInfo = medias.getObject(mediaIndex);

                //прибавление к высоте поста высоту каждой фотки + паддинг
                int mediaHeight = getMediaHeight(mediaInfo, postMediaWidth);
                postHeight += mediaHeight + PADDING;

                //сохранение высоты медиа в словарь
                String fileName = ITD.getFileName(mediaInfo.getString("url"));
                postsHeights.put(fileName, new Integer(mediaHeight));
            }

            ITD.log("Высота поста " + postHeight);
            return postHeight;
        }
        //если это репост
        else if (originalPost != null) {
            //прибавление к высоте поста высоты заголовка репоста
            postHeight += PADDING*3 + avatarSize + 2;

            //разбиение текста репоста по строкам, прибавление его высоты к высоте поста
            String[] repostContent = split(originalPost.getString("content"), fontPlain, screenWidth - PADDING*4 - 2);
            postHeight += (repostContent.length != 0) ? lineHeight*repostContent.length+PADDING : 0;

            //прибавление высот медиа, сохранение в словарь
            JSONArray repostAttachments = originalPost.getArray("attachments");
            if (!repostAttachments.isEmpty()) {
                for (int j = 0; j < repostAttachments.size(); j++) {
                    JSONObject attachmentInfo = repostAttachments.getObject(j);

                    int mediaHeight = getMediaHeight(attachmentInfo, repostMediaWidth);
                    postHeight += mediaHeight + PADDING;

                    String fileName = ITD.getFileName(attachmentInfo.getString("url"));
                    repostsMediaHeights.put(fileName, new Integer(mediaHeight));
                }
            }

            ITD.log("Высота поста " + postHeight);
            return postHeight;
        }

        ITD.log("Высота поста " + postHeight);
        return postHeight;
    }


    void initMediaLoader() {
        ITD.log("медиа поток");
        mediaLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    while (mediasQueue.isEmpty()) {
                        synchronized (mediasQueue) {
                            try {
                                mediasQueue.wait(); //пик шизы
                            } catch (Exception e) { ITD.log(String.valueOf(e)); } //ожидание реальность
                        }
                    }

                    ITD.log("Запрос на медиа " + mediasQueue);
                    Vector mediaRequest = (Vector) mediasQueue.elementAt(0);
                    String fileName = (String) mediaRequest.elementAt(0);
                    int type = ((Integer) mediaRequest.elementAt(1)).intValue();
                    String postId = (String) mediaRequest.elementAt(2);

                    if (type == POST || type == REPOST) {
                        int mediaWidth = type == REPOST ? repostMediaWidth : postMediaWidth;

                        Image media;
                        try {
                            String mediaUrl = ITD.URL + "/media/" + fileName + "?width=" + mediaWidth;
                            InputStream mediaRaw = ITD.rawGetRequest(mediaUrl);
                            media = Image.createImage(mediaRaw);

                            Hashtable heightsCache = type == REPOST ? repostsMediaHeights : postsHeights;
                            int mediaHeight = ((Integer) heightsCache.get(fileName)).intValue();
                            if (media.getHeight() != mediaHeight) {
                                ITD.log("НЕСОСТЫКОВКА " + media.getHeight() + " " + mediaHeight);
                                heightsCache.put(fileName, new Integer(media.getHeight()));

                                Integer newPostHeight = new Integer(((Integer) postsHeights.get(postId)).intValue() + media.getHeight() - mediaHeight);
                                postsHeights.put(postId, newPostHeight);
                            }
                        }
                        catch (Exception e) {
                            ITD.log("Ошибка создания медиа " + e);
                            media = Image.createImage(mediaWidth, 100);
                        }

                        medias.put(fileName, media);
                        mediasQueue.removeElementAt(0);
                    }
                    else if (type == BANNER) {
                        String bannerUrl = ITD.URL + "/banner/" + fileName + "?width=" + screenWidth;

                        Image banner;
                        try {
                            InputStream bannerRaw = ITD.rawGetRequest(bannerUrl);
                            banner = Image.createImage(bannerRaw);
                        } catch (Exception e) {
                            ITD.log("Ошибка создания баннера " + e);
                            banner = Image.createImage(screenWidth, 100);
                        }

                        medias.put("banner", banner);
                        mediasQueue.removeElementAt(0);
                    }

                    repaint();
                }
            }
        });

        ITD.log("медиа поток запуск");
        mediaLoader.start();
    }


    void initAvatarLoader() {
        ITD.log("аватар поток");
        avatarLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    while (avatarsQueue.isEmpty()) {
                        synchronized (avatarsQueue) {
                            try {
                                avatarsQueue.wait(); //пик шизы
                            } catch (Exception e) { ITD.log(String.valueOf(e)); } //ожидание реальность
                        }
                    }

                    String emojiId = (String) avatarsQueue.elementAt(0);
                    String avatarUrl = ITD.URL + "/avatar/" + emojiId + "?size=" + avatarSize;

                    InputStream avatarRaw = ITD.rawGetRequest(avatarUrl);
                    Image avatar;
                    try {
                        avatar = Image.createImage(avatarRaw);
                    } catch (Exception e) {
                        ITD.log("Ошибка создания аватара " + e);
                        avatar = Image.createImage(avatarSize, avatarSize);
                    }

                    avatars.put(emojiId, avatar);
                    avatarsQueue.removeElementAt(0);

                    repaint();
                }
            }
        });

        ITD.log("аватар поток запуск");
        avatarLoader.start();
    }


    void initPostLoader() {
        ITD.log("пост поток");
        postLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    synchronized (postLoadNotifier) {
                        try {
                            postLoadNotifier.wait();
                        } catch (Exception e) { ITD.log(String.valueOf(e)); }
                    }

                    loadPosts(ITD.POSTS_LIMIT);
                    arePostsRequested = false;
                    repaint();
                }
            }
        });

        ITD.log("пост поток запуск");
        postLoader.start();
    }


    private boolean loadPosts(int postsLimit) {
        try {
            midlet.startPrintln("Получение постов...");
            String url = URL_PARTS[0] + postsLimit + URL_PARTS[1];
            if (cursor != 0) url += URL_PARTS[2] + cursor;
            String postsResponse = ITD.getRequest(url, midlet.getRefreshToken(), true);

            midlet.startPrintln("Парсинг JSON...");
            JSONObject json = JSON.getObject(postsResponse);
            JSONArray posts = json.getObject("data").getArray("posts");
            if (posts.isEmpty()) return false;

            midlet.startPrintln("Добавление элементов...");
            for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
                elements.addElement(posts.get(postIndex));
            }
            cursor = Integer.parseInt(
                    json.getObject("data")
                            .getObject("pagination")
                            .getString("nextCursor")
            );
        }
        catch (Exception ignored) {
            midlet.startPrintln("Произошла ошибка");
            return false;
        }

        return true;
    }


    void requestPosts() {
        if (!arePostsRequested) {
            arePostsRequested = true;
            synchronized (postLoadNotifier) {
                postLoadNotifier.notify();
            }
        }
    }


    protected void paint(final Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        //чтобы почистить очередь областей нажатия
        if (hasPointerEvents()) {
            likesHitboxes = new Hashtable();
            repostsHitboxes = new Hashtable();
        }

        elementsHeightTemp = 0;

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int postIndex = 0; postIndex < elements.size(); postIndex++) {
            JSONObject post = (JSONObject) elements.elementAt(postIndex);
            boolean isSelected = selectedIndex == postIndex;
            if (isSelected) selectedY = currentY;

            drawPost(g, currentY, post, isSelected);
            // Сдвигаем курсор рисования вниз
            currentY += ((Integer) postsHeights.get(post.getString("id"))).intValue();
        }

        elementsHeight = elementsHeightTemp;

        if (scrollY + screenHeight >= elementsHeight) requestPosts();
        if (arePostsRequested) {
            String notification = "Прогрузка постов...";
            g.setColor(COLOR_POST_REQUEST_NOTIFY);
            int notifyWidth = strWidth(notification, fontBold);
            g.setFont(fontBold);
            g.drawString(
                    notification,
                    (screenWidth - notifyWidth) / 2,
                    PADDING*2,
                    Graphics.TOP | Graphics.LEFT
            );
        }
    }


    void drawPost(Graphics g, int currentY, JSONObject post, boolean isSelected) {
        final String id = post.getString("id");

        String[] content;
        if (postsStrings.contains(id)) {
            content = (String[]) postsStrings.get(id);
        }
        else {
            content = split(post.getString("content"), fontPlain, screenWidth - PADDING*2);
            postsStrings.put(id, content);
        }

        int postHeight = getElementHeight(post);
        elementsHeightTemp += postHeight;

        // Оптимизация: Рисуем, только если пост попадает в экран
        if (currentY + postHeight > 0 && currentY < screenHeight) {
            // Рисуем фон выделения, если пост выбран курсором
            if (isSelected) {
                if (showSelection) {
                    g.setColor(COLOR_SEL);
                    g.fillRect(0, currentY, screenWidth, postHeight);
                }

                if (!viewedPosts.contains(id)) {
                    ITD.log("Отправка просмотра " + id);
                    viewedPosts.addElement(id);
                    new Thread(new Runnable() {
                        public void run() {
                            ITD.postRequest(
                                    ITD.API_URL + "/posts/" + id + "/view",
                                    null,
                                    midlet.getRefreshToken()
                            );
                        }
                    }).start();
                }
            }
            //чтобы после перехода с сенсора на кнопки выделение было на посте посреди экрана:
            else if (!showSelection && -currentY + screenHeight/2 <= postHeight && currentY <= screenHeight/2){
                selectedIndex = elements.indexOf(post);
            }

            //содержимое поста
            drawPostContent(g, currentY, post, content, postsHeights, false);

            //репост
            JSONObject repost = post.getObject("originalPost");
            if (repost != null) {
                String repostId = repost.getString("id");
                int repostContentWidth = screenWidth - PADDING * 4 - 2;

                String[] repostContent;
                if (repostsStrings.contains(repostId)) {
                    repostContent = (String[]) postsStrings.get(repostId);
                }
                else {
                    repostContent = split(repost.getString("content"), fontPlain, repostContentWidth);
                    repostsStrings.put(repostId, content);
                }

                int repostY = currentY + PADDING*2 + avatarSize + lineHeight*content.length + 1;
                if (repostContent.length != 0) repostY += PADDING;
                g.drawRect( //рамка репоста
                        PADDING,
                        repostY,
                        postMediaWidth,
                        postHeight - (repostY - currentY) - PADDING*2 - iconSize
                );

                //содержимое репоста
                drawPostContent(g, repostY, repost, repostContent, repostsMediaHeights, true);
            }

            drawMetadata(g, currentY, postHeight, post);

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(0, currentY + postHeight - 1, screenWidth - 1, currentY + postHeight - 1);
        }
    }


    void drawPostContent(Graphics g, int currentY, JSONObject post,
                         String[] content, Hashtable mediaHeights, boolean isRepost) {
        int offset = isRepost ? PADDING+1 : 0;

        // Рисуем аватарку
        String emoji = post.getObject("author").getString("avatar");
        String emojiId = getEmojiId(emoji);

        if (avatars.containsKey(emojiId)) {
            if (avatars.get(emojiId) != requestMarker) {
                Image avatar = (Image) avatars.get(emojiId);
                g.drawImage(avatar, PADDING + offset, currentY + PADDING, 0);
            }
        }
        else {
            avatars.put(emojiId, requestMarker); //маркер реквеста
            synchronized (avatarsQueue) {
                avatarsQueue.addElement(emojiId);
                avatarsQueue.notify();
            }
        }

        // Рисуем Имя автора
        String displayName = post.getObject("author").getString("displayName");
        boolean hasNuksta = post.getObject("author").getBoolean("hasNuksta");
        g.setFont(fontBold);
        g.setColor(hasNuksta ? COLOR_NUKSTA : COLOR_TEXT);
        int userDataY = currentY + PADDING;
        g.drawString(
                displayName,
                PADDING * 2 + avatarSize + offset,
                userDataY,
                Graphics.TOP | Graphics.LEFT
        );
        int nameWidth = strWidth(displayName, fontPlain);

        //галочка
        boolean isVerified = post.getObject("author").getBoolean("verified");
        if (isVerified) {
            int verifiedX = Math.min(PADDING * 3 + avatarSize + nameWidth, screenWidth - iconSize - PADDING);
            g.drawImage(
                    verifiedIcon,
                    verifiedX + offset,
                    userDataY,
                    Graphics.TOP | Graphics.LEFT
            );
        }

        //время публикации
        int age = post.getInt("age");
        g.setFont(fontBold);
        g.setColor(COLOR_TEXT);
        g.drawString(
                age + " секунд назад",
                PADDING * 2 + avatarSize + offset,
                userDataY + avatarSize - lineHeight,
                Graphics.TOP | Graphics.LEFT
        );

//        if (!isRepost && hasPointerEvents()) {
//            g.drawImage(
//                    moreIcon,
//                    screenWidth - PADDING - iconSize,
//                    userDataY + (avatarSize - iconSize) / 2,
//                    Graphics.TOP | Graphics.LEFT
//            );
//        }

        // Рисуем Текст поста
        g.setFont(fontPlain);
        g.setColor(COLOR_TEXT);
        for (int j = 0; j < content.length; j++) {
            g.drawString(
                    content[j],
                    PADDING + offset,
                    currentY + PADDING*2 + avatarSize + lineHeight*j,
                    Graphics.TOP | Graphics.LEFT
            );
        }

        //прикреплённые медиа
        JSONArray attachments = post.getArray("attachments");
        if (!attachments.isEmpty()) {
            String postId = post.getString("id");

            for (int mediaIndex = 0; mediaIndex < attachments.size(); mediaIndex++) {
                JSONObject mediaInfo = attachments.getObject(mediaIndex);

                String url = mediaInfo.getString("url");
                String fileName = ITD.getFileName(url);

                int mediaHeight = ((Integer) mediaHeights.get(fileName)).intValue();
                int mediaY = currentY + PADDING*(3+mediaIndex) + avatarSize +
                        lineHeight*content.length + heightsSum(attachments, mediaHeights, mediaIndex);

                if (medias.containsKey(fileName) && medias.get(fileName) != requestMarker) {
                    Image media = (Image) medias.get(fileName);

                    g.drawImage(
                            media,
                            PADDING + offset,
                            mediaY,
                            0
                    );
                }
                else {
                    g.setColor(COLOR_LOADING);
                    g.fillRect(
                            PADDING + offset,
                            mediaY,
                            isRepost ? repostMediaWidth : postMediaWidth,
                            mediaHeight
                    );

                    if (!medias.containsKey(fileName)) {
                        medias.put(fileName, requestMarker); //маркер реквеста

                        Vector mediaRequest = new Vector(3);

                        mediaRequest.addElement(fileName);
                        mediaRequest.addElement(new Integer(isRepost ? REPOST : POST));
                        mediaRequest.addElement(postId);

                        mediasQueue.addElement(mediaRequest);

                        synchronized (mediasQueue) {
                            mediasQueue.notify();
                        }
                    }
                }
            }
        }
    }


    private void drawMetadata(Graphics g, int currentY, int postHeight, JSONObject post) {
        //Y координата для всех метаданных внизу поста
        int metadataY = currentY + postHeight - PADDING - iconSize;
        g.setColor(COLOR_TEXT);

        // Рисуем Лайки
        boolean isLiked = post.getBoolean("isLiked");
        int likesCount = post.getInt("likesCount");
        g.drawImage(
                isLiked ? likeFillIcon : likeIcon,
                PADDING,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        String likesStr = String.valueOf(likesCount);
        g.drawString(
                likesStr,
                iconSize + PADDING*2,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        int likesWidth = iconSize + PADDING + strWidth(likesStr, fontPlain);
        //границы сенсорной кнопки лайка
        if (hasPointerEvents()) {
            likesHitboxes.put(new int[] {
                    PADDING,
                    metadataY,
                    PADDING + likesWidth,
                    metadataY + iconSize
            }, post);
        }

        //комменты
        int commentsCount = post.getInt("commentsCount");
        g.drawImage(
                commentIcon,
                PADDING*3 + likesWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        String commentStr = String.valueOf(commentsCount);
        g.drawString(
                commentStr,
                iconSize + PADDING*4 + likesWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        int commentsWidth = iconSize + PADDING + strWidth(commentStr, fontPlain);

        //репосты
        int repostsCount = post.getInt("repostsCount");
        g.drawImage(
                repostIcon,
                PADDING*5 + likesWidth + commentsWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        String repostsStr = String.valueOf(repostsCount);
        g.drawString(
                repostsStr,
                iconSize + PADDING*6 + likesWidth + commentsWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        int repostsWidth = iconSize + PADDING + strWidth(repostsStr, fontPlain);
        //границы сенсорной кнопки лайка
        if (hasPointerEvents()) {
            repostsHitboxes.put(new int[] {
                    PADDING*5 + likesWidth + commentsWidth,
                    metadataY,
                    PADDING*5 + likesWidth + commentsWidth + repostsWidth,
                    metadataY + iconSize
            }, post);
        }

        //просмотры
        int viewsCount = post.getInt("viewsCount");
        String viewStr = String.valueOf(viewsCount);
        int viewOffset = screenWidth - PADDING*2 - iconSize - strWidth(viewStr, fontPlain);
        g.drawImage(
                viewIcon,
                viewOffset,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        g.drawString(
                viewStr,
                viewOffset + iconSize + PADDING,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
    }


    protected void hitBoxesCheck(int x, int y) {
        Enumeration likeHbEnumKeys = likesHitboxes.keys();
        while (likeHbEnumKeys.hasMoreElements()) {
            int[] c /*coords*/ = (int[]) likeHbEnumKeys.nextElement();
            if (c[0] <= x && x <= c[2] && c[1] <= y && y <= c[3]) {
                ITD.log("Отправка лайка");
                likePost((JSONObject) likesHitboxes.get(c));
                break;
            }
        }

        Enumeration repostHbEnumKeys = repostsHitboxes.keys();
        while (repostHbEnumKeys.hasMoreElements()) {
            int[] c /*coords*/ = (int[]) repostHbEnumKeys.nextElement();
            if (c[0] <= x && x <= c[2] && c[1] <= y && y <= c[3]) {
                ITD.log("Запуск окна репоста");
                repostPost((JSONObject) repostsHitboxes.get(c));
                break;
            }
        }
    }


    void likePost(final JSONObject post) {
        boolean isLiked = post.getBoolean("isLiked");
        isLiked = !isLiked;
        post.put("isLiked", isLiked);

        int likesCount = post.getInt("likesCount");
        post.put("likesCount", isLiked ? likesCount + 1 : likesCount - 1);

        repaint();

        final boolean fIsLiked = isLiked;
        final String url = ITD.API_URL + "/posts/" + post.getString("id") + "/like";
        new Thread(new Runnable() {
            public void run() {
                if (fIsLiked) {
                    ITD.postRequest(url, new byte[]{}, midlet.getRefreshToken());
                }
                else {
                    ITD.deleteRequest(url, new byte[]{}, midlet.getRefreshToken());
                }
            }
        }).start();
    }


    void likePost() {
        JSONObject post = (JSONObject) elements.elementAt(selectedIndex);
        likePost(post);
    }


    void repostPost(JSONObject post) {
        String postId = post.getString("id");
        String name = post.getObject("author").getString("displayName");
        midlet.initWriter(REPOST, null, postId, name, this);
    }


    void repostPost() {
        JSONObject post = (JSONObject) elements.elementAt(selectedIndex);
        repostPost(post);
    }


    //попросил нейронку вырезать слайсер из мпграма, но похоже на нейрослоп
    public static String[] split(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) {
            return new String[0];
        }

        Vector lines = new Vector();
        int len = text.length();
        int start = 0;
        int currentWidth = 0;
        int lastSpaceIndex = -1;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // 1. Обработка принудительного переноса строки (\n)
            if (c == '\n') {
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
                    lines.addElement(text.substring(start, lastSpaceIndex));
                    start = lastSpaceIndex + 1; // Следующая строка начинается после пробела
                    i = start - 1; // "Откатываем" цикл назад к началу нового слова
                } else {
                    // Пробелов не было (очень длинное слово), режем жестко по букве
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
            lines.addElement(text.substring(start));
        }

        // Конвертация Vector в массив String[] (для скорости чтения в paint)
        String[] result = new String[lines.size()];
        lines.copyInto(result);
        return result;
    }


    static int strWidth(String str, Font font) {
        return font.charsWidth(str.toCharArray(), 0, str.length());
    }


    static String getEmojiId(String emoji) {
        char[] emojiChar = emoji.toCharArray();

        String emojiId = "";
        for (int charIndex = 0; charIndex < emojiChar.length; charIndex++) {
            int charCode = emojiChar[charIndex];
            emojiId += Integer.toHexString(charCode);
        }

        return emojiId;
    }


    static int heightsSum(JSONArray attachments, Hashtable mediaHeights, int mediaIndex) {
        int mediaHeightsSum = 0;
        for (int i = 0; i < mediaIndex; i++) {
            String fileName = ITD.getFileName(attachments.getObject(i).getString("url"));
            mediaHeightsSum += ((Integer) mediaHeights.get(fileName)).intValue();
        }
        return mediaHeightsSum;
    }


    int getMediaHeight(JSONObject attachmentInfo, int mediaWidth) {
        int width = attachmentInfo.getInt("width");
        int height = attachmentInfo.getInt("height");

        float ratio = Math.max(Math.min((float) height / (float) width, MAX_MEDIA_RATIO), 1f / MAX_MEDIA_RATIO); //ограничение соотношения сторон до 1 к 3
        int mediaHeight = (int) Math.ceil(mediaWidth * ratio);

        return mediaHeight;
    }


    public void stopFeed() {
        avatarLoader.interrupt();
        mediaLoader.interrupt();
        postLoader.interrupt();
        scrollThread.interrupt();
        setCommandListener(null);
        removeCommand(midlet.backToMenuCmd);
        removeNontouchCmds();
    }


    protected void addNontouchCmds() {
        addCommand(midlet.likeCmd);
        addCommand(midlet.selectCmd);
        addCommand(midlet.repostCmd);
    }


    protected void removeNontouchCmds() {
        removeCommand(midlet.likeCmd);
        removeCommand(midlet.selectCmd);
        removeCommand(midlet.repostCmd);
    }
}