package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

public class FeedCanvas extends Canvas {
    JSONArray posts;
    ITD midlet;

    Hashtable postsStrings = new Hashtable(); //словарь из массивов со строками постов
    Hashtable repostsStrings = new Hashtable();
    Hashtable postsHeights = new Hashtable(); //высоты постов и отдельных медиа постов
    Hashtable repostsMediaHeights = new Hashtable(); //высоты репостов и отдельных медиа репостов

    Hashtable avatars = new Hashtable();
    Vector avatarsQueue = new Vector();
    Thread avatarLoader;

    Hashtable medias = new Hashtable();
    Vector mediasQueue = new Vector();
    Thread mediaLoader;

    // Параметры UI
    int scrollY = 0;         // Смещение прокрутки по вертикали
    int selectedIndex = 0;   // Индекс выбранного поста
    int screenWidth;
    int screenHeight;

    // Шрифты
    Font fontBold;
    Font fontPlain;
    int lineHeight;

    // Константы для верстки
    static final int PADDING = 5;
    static final int AVATAR_SIZE = 32;
    static final int ICON_SIZE = 16;
    static final int COLOR_BG = 0x000000;
    static final int COLOR_TEXT = 0xE4E6E8;
    static final int COLOR_SEL = 0x242424;
    static final int COLOR_BLUE = 0x0000FF;
    static final int MIN_POST_HEIGHT = AVATAR_SIZE + PADDING*2;
    static final float MAX_MEDIA_RATIO = 3f;

    static final int SCROLL_HEIGHT = 100;

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

    int headerHeight = 0;

    public FeedCanvas(JSONArray posts, ITD midlet) {
        this.posts = posts;
        this.midlet = midlet;

        setFullScreenMode(false);
        setScreenSize();
        initFonts();
        initIcons();

        initAvatarLoader();
        initMediaLoader();

        setCommandListener(midlet.feedCmdListener);
        addCommand(midlet.likeCmd);
        addCommand(midlet.selectCmd);
        addCommand(midlet.backToMenuCmd);

        ITD.log("фид стартовал");
    }


    void setScreenSize() {
        screenWidth = getWidth();
        screenHeight = getHeight();
        postMediaWidth = screenWidth - PADDING*2;
        repostMediaWidth = screenWidth - PADDING*4 - 2;
    }


    void initFonts() {
        fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        lineHeight = fontPlain.getHeight();
    }


    void initIcons() {
        try {
            likeIcon = ITD.getIconRes("like");
            likeFillIcon = ITD.getIconRes("like_fill");
            commentIcon = ITD.getIconRes("comment");
            viewIcon = ITD.getIconRes("view");
            repostIcon = ITD.getIconRes("repost");
            verifiedIcon = ITD.getIconRes("verified");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
    }


//    void calcHeights(JSONArray posts) { //расчёт высот всего что может пригодиться
//        for (int i = 0; i < posts.size(); i++) {
//            //получение поста, репостнутого поста и вложений
//            JSONObject post = (JSONObject) posts.get(i);
//            int postHeight = calcPostHeight(post);
//            //сохранение высоты поста
//            postsHeights.addElement(new Integer (postHeight));
//        }
//    }


    private int getPostHeight(JSONObject post) {
        String postId = post.getString("id");

        if (postsHeights.contains(postId)) {
            return ((Integer) postsHeights.get(postId)).intValue();
        }

        return calcPostHeight(post);
    }


    private int calcPostHeight(JSONObject post) {
        //разбиение текста поста по строкам
        String[] content = split(post.getString("content"), fontPlain, screenWidth - PADDING*2);

        //расчёт высоты поста с учётом только главного текста
        int postHeight = Math.max(PADDING*4 + AVATAR_SIZE + lineHeight * (content.length + 1), MIN_POST_HEIGHT);

        //расчёт высоты контента поста (при наличии)
        //если в посте есть фото/медиа
        JSONArray medias = post.getArray("attachments");
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
        JSONObject originalPost = post.getObject("originalPost");
        if (originalPost != null) {
            //прибавление к высоте поста высоты заголовка репоста
            postHeight += PADDING*3 + AVATAR_SIZE + 2;

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

                    Vector mediaRequest = (Vector) mediasQueue.elementAt(0);
                    String fileName = (String) mediaRequest.elementAt(0);
                    boolean isRepost = ((Boolean) mediaRequest.elementAt(1)).booleanValue();
                    String postId = (String) mediaRequest.elementAt(2);

                    int mediaWidth = isRepost ? postMediaWidth : repostMediaWidth;
                    String mediaUrl = ITD.URL + "/media/" + fileName + "?width=" + mediaWidth;

                    Hashtable heightsCache = isRepost ? postsHeights : repostsMediaHeights;
                    InputStream mediaRaw = ITD.rawGetRequest(mediaUrl);
                    Image media;
                    try {
                        media = Image.createImage(mediaRaw);

                        int mediaHeight = ((Integer) heightsCache.get(fileName)).intValue();
                        if (media.getHeight() != mediaHeight) {
                            ITD.log("НЕСОСТЫКОВКА " + media.getHeight() + " " + mediaHeight);
                            heightsCache.put(fileName, new Integer(media.getHeight()));

                            Integer newPostHeight = new Integer(((Integer) postsHeights.get(postId)).intValue() + media.getHeight() - mediaHeight);
                            postsHeights.put(postId, newPostHeight);

                            repaint();
                            return;
                        }
                    }
                    catch (Exception e) {
                        ITD.log("Ошибка создания медиа " + e);
                        media = Image.createImage(mediaWidth, 100);
                    }

                    medias.put(fileName, media);
                    mediasQueue.removeElementAt(0);

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
                    String avatarUrl = ITD.URL + "/avatar/" + emojiId;

                    InputStream avatarRaw = ITD.rawGetRequest(avatarUrl);
                    Image avatar = Image.createImage(AVATAR_SIZE, AVATAR_SIZE);
                    try {
                        avatar = Image.createImage(avatarRaw);
                    } catch (IOException e) { ITD.log("Ошибка создания аватара " + e); }

                    avatars.put(emojiId, avatar);
                    avatarsQueue.removeElementAt(0);

                    repaint();
                }
            }
        });

        ITD.log("аватар поток запуск");
        avatarLoader.start();
    }


    protected void paint(final Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            JSONObject post = (JSONObject) posts.get(postIndex);
            drawPost(g, currentY, post, postIndex == selectedIndex);

            // Сдвигаем курсор рисования вниз
            currentY += getPostHeight(post);
        }
    }


    void drawPost(Graphics g, int currentY, JSONObject post, boolean isSelected) {
        String id = post.getString("id");

        String[] content;
        if (postsStrings.contains(id)) {
            content = (String[]) postsStrings.get(id);
        }
        else {
            content = split(post.getString("content"), fontPlain, screenWidth - PADDING*2);
            postsStrings.put(id, content);
        }

        int postHeight = getPostHeight(post);

        // Оптимизация: Рисуем, только если пост попадает в экран
        if (currentY + postHeight > 0 && currentY < screenHeight) {
            // Рисуем фон выделения, если пост выбран курсором
            if (isSelected) {
                g.setColor(COLOR_SEL);
                g.fillRect(0, currentY, screenWidth, postHeight);
            }

            //содержимое поста
            drawPostContent(g, post, currentY, content, postsHeights, 0);

            //репост
            JSONObject repost = post.getObject("originalPost");
            if (repost != null) {
                String repostId = repost.getString("id");

                int repostY = currentY + PADDING * 3 + AVATAR_SIZE + lineHeight * content.length + 1;
                g.drawRect(
                        PADDING + 1,
                        repostY,
                        postMediaWidth - 2,
                        postHeight - repostY - PADDING - ICON_SIZE
                );
                int repostWidth = screenWidth - PADDING * 4 - 2;

                String[] repostContent;
                if (repostsStrings.contains(repostId)) {
                    repostContent = (String[]) postsStrings.get(repostId);
                }
                else {
                    repostContent = split(repost.getString("content"), fontPlain, repostWidth);
                    repostsStrings.put(repostId, content);
                }

                //содержимое репоста
                drawPostContent(g, repostY, repost, repostContent, repostsMediaHeights, PADDING+1);
            }

            drawMetadata(g, currentY, postHeight, post);

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(0, currentY + postHeight - 1, screenWidth, currentY + postHeight - 1);
        }
    }


    private void drawMetadata(Graphics g, int currentY, int postHeight, JSONObject post) {
        //Y координата для всех метаданных внизу поста
        int metadataY = currentY + postHeight - PADDING - ICON_SIZE;
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
                ICON_SIZE + PADDING*2,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        int likesWidth = ICON_SIZE + PADDING + strWidth(likesStr, fontPlain);

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
                ICON_SIZE + PADDING*4 + likesWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        int commentsWidth = ICON_SIZE + PADDING + strWidth(commentStr, fontPlain);

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
                ICON_SIZE + PADDING*6 + likesWidth + commentsWidth,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
//                int repostsWidth = ICON_SIZE + PADDING + strWidth(repostStr, fontPlain);

        //просмотры
        int viewsCount = post.getInt("viewsCount");
        String viewStr = String.valueOf(viewsCount);
        int viewOffset = screenWidth - PADDING*2 - ICON_SIZE - strWidth(viewStr, fontPlain);
        g.drawImage(
                viewIcon,
                viewOffset,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
        g.drawString(
                viewStr,
                viewOffset + ICON_SIZE + PADDING,
                metadataY,
                Graphics.TOP | Graphics.LEFT
        );
    }


    protected void keyPressed(int keyCode) { //обработка нажатий клавиш
        int action = getGameAction(keyCode);

        int selectedPostHeight = ((Integer) postsHeights.elementAt(selectedIndex)).intValue();
        int scrolledHeight = ITD.sum(postsHeights, 0, selectedIndex);

        if (action == UP) {
            onUp(selectedPostHeight, scrolledHeight);
        }
        else if (action == DOWN) {
            onDown(selectedPostHeight, scrolledHeight, posts.size());
        }
//        else if (action == FIRE) {
//            likePost();
//        }

        // Обязательно вызываем перерисовку после изменений!
        ITD.log(scrollY);
        repaint();
    }


    void likePost() {
        JSONObject post = (JSONObject) posts.get(selectedIndex);
        boolean isLiked = post.getBoolean("isLiked");
        isLiked = !isLiked;

        String url = ITD.API_URL + "/posts/" + post.getString("id") + "/like";
        if (isLiked) {
            ITD.postRequest(url, new byte[]{}, midlet.getRefreshToken());
        }
        else {
            ITD.deleteRequest(url, new byte[]{}, midlet.getRefreshToken());
        }

        post.put("isLiked", isLiked);
        posts.remove(selectedIndex);
        posts.put(selectedIndex, post);

        repaint();
    }


    void onDown(int selectedPostHeight, int scrolledHeight, int postsAmount) {
        if (selectedIndex < postsAmount - 1) { // если не последний пост
            int nextPostHeight = ((Integer) postsHeights.elementAt(selectedIndex + 1)).intValue();

            // Логика "умного" скролла вниз
            if (selectedPostHeight > screenHeight) { // если текущий пост выше чем экран
                if (scrollY + screenHeight == scrolledHeight + selectedPostHeight) { // если самый конец поста
                    if (nextPostHeight > screenHeight) { // если следующий пост выше экрана
                        selectedIndex++;
                        scrollY = scrolledHeight + selectedPostHeight; // прокрутить в самое начало следующего поста
                        ITD.log("scroll down state 1");
                    }
                    else {
                        selectedIndex++;
                        scrollY = scrolledHeight + selectedPostHeight + nextPostHeight - screenHeight; // прокрутить в конец следующего поста
                        ITD.log("scroll down state 2");
                    }
                }
                else if (scrolledHeight + selectedPostHeight - (scrollY + screenHeight) < SCROLL_HEIGHT) { // если до низа поста осталось меньше чем значение прокрутки
                    scrollY = scrolledHeight + selectedPostHeight - screenHeight; // докрутить до конца поста
                    ITD.log("scroll down state 3");
                }
                else {
                    scrollY += SCROLL_HEIGHT; // прокрутить на значение прокрутки вниз
                    ITD.log("scroll down state 4");
                }
            }
            else {
                if (nextPostHeight > screenHeight) { // если следующий пост выше экрана
                    selectedIndex++;
                    scrollY = scrolledHeight + selectedPostHeight; // прокрутить в начало следующего поста
                    ITD.log("scroll down state 5");
                }
                else {
                    selectedIndex++;
                    scrollY = Math.max(scrolledHeight + selectedPostHeight + nextPostHeight - screenHeight, 0); // прокрутить в конец следующего поста, ограничение чтобы не уезжать за верх
                    ITD.log("scroll down state 6");
                }
            }
            ITD.log(selectedPostHeight);
//                }
        }
        else if (selectedPostHeight > screenHeight) { // если последний пост и он выше чем экран
            if (scrolledHeight + selectedPostHeight - (scrollY + screenHeight) < SCROLL_HEIGHT) { // если до низа поста осталось меньше чем значение прокрутки
                scrollY = scrolledHeight + selectedPostHeight - screenHeight; // докрутить до конца поста
                ITD.log("scroll down state 7");
            }
            else {
                scrollY += SCROLL_HEIGHT; // прокрутить на значение прокрутки вниз
                ITD.log("scroll down state 8");
            }
        }
    }


    void onUp(int selectedPostHeight, int scrolledHeight) {
        if (selectedIndex > 0) {
            int prevPostHeight = ((Integer) postsHeights.elementAt(selectedIndex - 1)).intValue();

            // Логика "умного" скролла вверх
            if (selectedPostHeight > screenHeight) {
                if (scrollY == scrolledHeight) {
                    if (prevPostHeight > screenHeight) {
                        selectedIndex--;
                        scrollY = scrolledHeight - screenHeight;
                        ITD.log("scroll up state 1");
                    }
                    else {
                        selectedIndex--;
                        scrollY = scrolledHeight - prevPostHeight;
                        ITD.log("scroll up state 2");
                    }
                }
                else if (scrollY - scrolledHeight < SCROLL_HEIGHT) {
                    scrollY = scrolledHeight;
                    ITD.log("scroll up state 3");
                }
                else {
                    scrollY = scrollY - SCROLL_HEIGHT;
                    ITD.log("scroll up state 4");
                }
            }
            else {
                if (prevPostHeight > screenHeight) {
                    selectedIndex--;
                    scrollY = scrolledHeight - screenHeight;
                    ITD.log("scroll up state 5");
                }
                else {
                    selectedIndex--;
                    scrollY = Math.max(Math.min(scrolledHeight - prevPostHeight, scrolledHeight + selectedPostHeight - screenHeight), 0);
                    ITD.log("scroll up state 6");
                }
            }
            ITD.log(selectedPostHeight);
        }
        else if (selectedPostHeight > screenHeight) {
            if (scrollY - scrolledHeight < SCROLL_HEIGHT) {
                scrollY = scrolledHeight;
                ITD.log("scroll up state 7");
            }
            else {
                scrollY = scrollY - SCROLL_HEIGHT;
                ITD.log("scroll up state 8");
            }
        }
    }


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


    void drawPostContent(Graphics g, JSONObject post, int currentY,
                         String[] content, Hashtable mediaHeights, int offset) {
        // Рисуем аватарку
        String emoji = post.getObject("author").getString("avatar");
        String emojiId = getEmojiId(emoji);

        if (avatars.containsKey(emojiId)) {
            Image avatar = (Image) avatars.get(emojiId);
            g.drawImage(avatar, PADDING + offset, currentY + PADDING, 0);
        }
        else {
            synchronized (avatarsQueue) {
                avatarsQueue.addElement(emojiId);
                avatarsQueue.notify();
            }
        }

        // Рисуем Имя автора
        String displayName = post.getObject("author").getString("displayName");
        g.setFont(fontBold);
        g.setColor(COLOR_TEXT);
        int userDataY = currentY + PADDING;
        g.drawString(
                displayName,
                PADDING * 2 + AVATAR_SIZE + offset,
                userDataY,
                Graphics.TOP | Graphics.LEFT
        );
        int nameWidth = strWidth(displayName, fontPlain);

        //галочка
        boolean isVerified = post.getObject("author").getBoolean("verified");
        if (isVerified) {
            int verifiedX = Math.min(PADDING * 3 + AVATAR_SIZE + nameWidth, screenWidth - ICON_SIZE - PADDING);
            g.drawImage(
                    verifiedIcon,
                    verifiedX + offset,
                    userDataY,
                    Graphics.TOP | Graphics.LEFT
            );
        }

        //время публикации
        int createdAt = post.getInt("createdAt");
        g.setFont(fontBold);
        g.setColor(COLOR_TEXT);
        g.drawString(
                createdAt + " секунд назад",
                PADDING * 2 + AVATAR_SIZE + offset,
                currentY + PADDING*2 + lineHeight - 2,
                Graphics.TOP | Graphics.LEFT
        );

        // Рисуем Текст поста
        g.setFont(fontPlain);
        g.setColor(COLOR_TEXT);
        for (int j = 0; j < content.length; j++) {
            g.drawString(
                    content[j],
                    PADDING + offset,
                    currentY + PADDING*2 + AVATAR_SIZE + lineHeight*j,
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

                if (medias.containsKey(fileName)) {
                    Image media = (Image) medias.get(fileName);
                    int mediaHeight = ((Integer) mediaHeights.get(fileName)).intValue();

                    g.drawImage(
                            media,
                            PADDING + offset,
                            currentY + PADDING*(3+mediaIndex) + AVATAR_SIZE +
                                    lineHeight*content.length + heightsSum(attachments, mediaHeights, mediaIndex),
                            0
                    );
                }
                else {
                    synchronized (mediasQueue) {
                        Vector mediaRequest = new Vector();

                        mediaRequest.addElement(fileName);
                        mediaRequest.addElement(new Integer(offset));
                        mediaRequest.addElement(postId);

                        mediasQueue.addElement(mediaRequest);
                        mediasQueue.notify();
                    }
                }
            }
        }
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
        setCommandListener(null);
        removeCommand(midlet.backToMenuCmd);
    }
}