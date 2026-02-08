package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

public class FeedCanvas extends Canvas {
    private JSONArray posts;
    private ITD midlet;

    private Vector strings = new Vector(); //вектор из массивов со строками постов
    private Vector repostStrings = new Vector();
    private Vector postsHeights = new Vector(); //высоты постов
//    private Hashtable mediaPosts = new Hashtable();
    private Hashtable mediaHeights = new Hashtable(); //высоты отдельных медиа
    private Vector repostHeights = new Vector();

    private Vector repostsMediaHeights = new Vector(); //высоты отдельных медиа в репостах
//    private Vector postsMediaHeights = new Vector(); //высоты блоков медиа

    private Hashtable avatars = new Hashtable();
    private Vector avatarsQueue = new Vector();
    private Thread avatarLoader;

    private Hashtable medias = new Hashtable();
    private Vector mediasQueue = new Vector();
    private Thread mediaLoader;

    // Параметры UI
    private int scrollY = 0;         // Смещение прокрутки по вертикали
    private int selectedIndex = 0;   // Индекс выбранного поста
    private final int screenWidth;
    private final int screenHeight;

    // Шрифты
    private final Font fontBold;
    private final Font fontPlain;
    private final int lineHeight;

    // Константы для верстки
    private static final int PADDING = 5;
    private static final int AVATAR_SIZE = 32;
    private static final int ICON_SIZE = 16;
    private static final int COLOR_BG = 0x000000;
    private static final int COLOR_TEXT = 0xE4E6E8;
    private static final int COLOR_SEL = 0x242424;
    private static final int COLOR_BLUE = 0x0000FF;
    private static final int MIN_POST_HEIGHT = AVATAR_SIZE + PADDING*2;
    private static final float MAX_MEDIA_RATIO = 3f;

    private final int mediaWidth;

    //иконки
    //google material symbols, Apache License, Version 2.0
    //size 16, weight 400, grade -25, optical size 20, #E4E6E8
    private Image likeIcon;
    private Image likeFillIcon;
    private Image commentIcon;
    private Image viewIcon;
    private Image repostIcon;
    private Image verifiedIcon;


    public FeedCanvas(JSONArray posts, ITD midlet) {
        setFullScreenMode(true);
        screenWidth = getWidth();
        screenHeight = getHeight();
        mediaWidth = screenWidth - PADDING*2;

        // Инициализация шрифтов
        fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        lineHeight = fontPlain.getHeight();

        try {
            likeIcon = Image.createImage(Class.class.getResourceAsStream("/like.png"));
            likeFillIcon = Image.createImage(Class.class.getResourceAsStream("/like_fill.png"));
            commentIcon = Image.createImage(Class.class.getResourceAsStream("/comment.png"));
            viewIcon = Image.createImage(Class.class.getResourceAsStream("/view.png"));
            repostIcon = Image.createImage(Class.class.getResourceAsStream("/repost.png"));
            verifiedIcon = Image.createImage(Class.class.getResourceAsStream("/verified.png"));
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        this.posts = posts;
        this.midlet = midlet;

        for (int i = 0; i < posts.size(); i++) {
            JSONObject post = (JSONObject) posts.get(i);
            JSONObject originalPost = post.getObject("originalPost");
            JSONArray attachments = post.getArray("attachments");
            String[] content = split(post.getString("content"), fontPlain, screenWidth - PADDING*2);
            strings.addElement(content);

            int postHeight = Math.max(PADDING*4 + AVATAR_SIZE + lineHeight * (content.length + 1), MIN_POST_HEIGHT);

            int postContentHeight = 0;
//            Vector postMediaHeightsVector = new Vector();
            if (!attachments.isEmpty()) {
                for (int j = 0; j < attachments.size(); j++) {
                    JSONObject attachmentInfo = attachments.getObject(j);

                    int mediaHeight = getMediaHeight(attachmentInfo, mediaWidth);
                    postContentHeight += mediaHeight + PADDING;

                    String fileName = ITD.getFileName(attachmentInfo.getString("url"));
                    mediaHeights.put(fileName, new Integer(mediaHeight));
                }
            }
            else if (originalPost != null) {
                postContentHeight += PADDING*3 + AVATAR_SIZE + 2;

                String[] repostContent = split(originalPost.getString("content"), fontPlain, screenWidth - PADDING*4 - 2);
                if (repostContent.length != 0) {
                    postContentHeight += lineHeight * repostContent.length + PADDING;
                }
                repostStrings.addElement(repostContent);

                JSONArray repostAttachments = originalPost.getArray("attachments");
                if (!repostAttachments.isEmpty()) {
                    for (int j = 0; j < repostAttachments.size(); j++) {
                        JSONObject attachmentInfo = repostAttachments.getObject(j);

                        int mediaHeight = getMediaHeight(attachmentInfo, mediaWidth - PADDING*2 - 2);
                        postContentHeight += mediaHeight + PADDING;

                        String fileName = ITD.getFileName(attachmentInfo.getString("url"));
                        mediaHeights.put(fileName, new Integer(mediaHeight));
                    }
                }
                repostHeights.addElement(new Integer(postContentHeight));
            }
            postHeight += postContentHeight;

            if (originalPost == null) {
                repostStrings.addElement(null);
                repostHeights.addElement(null);
            }

//            postsMediaHeights.addElement(new Integer(postMediaHeight));

//            Integer[] postMediaHeights = new Integer[postMediaHeightsVector.size()];
//            postMediaHeightsVector.copyInto(postMediaHeights);

            postsHeights.addElement(new Integer (postHeight));
        }

        avatarLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    while (avatarsQueue.isEmpty()) {
                        synchronized (avatarsQueue) {
                            try {
                                avatarsQueue.wait(); //пик шизы
                            } catch (InterruptedException e) { throw new RuntimeException(e.toString()); }
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
        mediaLoader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    while (mediasQueue.isEmpty()) {
                        synchronized (mediasQueue) {
                            try {
                                mediasQueue.wait(); //пик шизы
                            } catch (InterruptedException e) { throw new RuntimeException(e.toString()); }
                        }
                    }

                    Vector mediaRequest = (Vector) mediasQueue.elementAt(0);
                    String fileName = (String) mediaRequest.elementAt(0);
                    int offset = ((Integer) mediaRequest.elementAt(1)).intValue();
                    int mediaWidth = screenWidth - PADDING*2 - offset*2;
                    String mediaUrl = ITD.URL + "/media/" + fileName + "?width=" + mediaWidth;

                    InputStream avatarRaw = ITD.rawGetRequest(mediaUrl);
                    Image media = Image.createImage(AVATAR_SIZE, AVATAR_SIZE);
                    try {
                        media = Image.createImage(avatarRaw);
                    } catch (IOException e) { ITD.log("Ошибка создания аватара " + e); }

                    int mediaHeight = ((Integer) mediaHeights.get(fileName)).intValue();
                    if (media.getHeight() != mediaHeight) {
                        ITD.log("НЕСОСТЫКОВКА " + media.getHeight() + " " + mediaHeight);
//                        postMediaHeights[mediaIndex] = new Integer(attach.getHeight());
                        mediaHeights.put(fileName, new Integer(media.getHeight()));

                        int postIndex = ((Integer) mediaRequest.elementAt(2)).intValue();
                        Integer newPostHeight = new Integer(((Integer) postsHeights.elementAt(postIndex)).intValue() + (media.getHeight() - mediaHeight));
                        postsHeights.setElementAt(newPostHeight, postIndex);

                        repaint();
                        return;
                    }

                    medias.put(fileName, media);
                    mediasQueue.removeElementAt(0);

                    repaint();
                }
            }
        });
        avatarLoader.start();
        mediaLoader.start();
    }


    // === ГЛАВНЫЙ МЕТОД ОТРИСОВКИ ===
    protected void paint(final Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            JSONObject post = (JSONObject) posts.get(postIndex);

            // Рассчитываем высоту этого поста
            int postHeight = ((Integer) postsHeights.elementAt(postIndex)).intValue();
            String[] content = (String[]) strings.elementAt(postIndex);

            // Оптимизация: Рисуем, только если пост попадает в экран
            if (currentY + postHeight > 0 && currentY < screenHeight) {
                JSONObject originalPost = post.getObject("originalPost");

                // Рисуем фон выделения, если пост выбран курсором
                if (postIndex == selectedIndex) {
                    g.setColor(COLOR_SEL);
                    g.fillRect(0, currentY, screenWidth, postHeight);
                }

                //содержимое поста
                renderPostContent(g, post, currentY, content, mediaHeights, 0, postIndex);

                //репост
                if (originalPost != null) {
                    int repostY = currentY + PADDING*3 + AVATAR_SIZE + lineHeight*content.length + 1;
                    g.drawRect(
                            PADDING+1,
                            repostY,
                            mediaWidth-2,
                            ITD.toInt(repostHeights.elementAt(postIndex)) - PADDING
                    );
                    int repostWidth = screenWidth - PADDING*4 - 2;
                    String[] repostContent = (String[]) repostStrings.elementAt(postIndex);
//                    g.drawString(
//                            "РЕПОООООСТ",
//                            PADDING,
//                            repostY,
//                            Graphics.TOP | Graphics.LEFT
//                    );

                    //содержимое репоста
                    renderPostContent(g, originalPost, repostY, repostContent, mediaHeights, PADDING+1, postIndex);
                }

//                int metadataY = currentY + PADDING*3 + AVATAR_SIZE + lineHeight * content.length;

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

                // Разделительная линия
                g.setColor(COLOR_SEL);
                g.drawLine(0, currentY + postHeight - 1, screenWidth, currentY + postHeight - 1);
            }

            // Сдвигаем курсор рисования вниз
            currentY += postHeight;
        }
    }


    // === ОБРАБОТКА НАЖАТИЙ КЛАВИШ ===
    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);

        if (action == UP) {
            if (selectedIndex > 0) {
                int selectedPostHeight = ((Integer) postsHeights.elementAt(selectedIndex)).intValue();

                if (selectedPostHeight > screenHeight) {
                    scrollY -= 50;
                }
                else {
                    selectedIndex--;

                    int scrolledHeight = 0;
                    for (int postIndex = 0; postIndex < selectedIndex; postIndex++) {
                        int postHeight = ((Integer) postsHeights.elementAt(postIndex)).intValue();
                        scrolledHeight += Math.max(postHeight, MIN_POST_HEIGHT);
                    }

                    // Логика "умного" скролла вверх
                    if (scrolledHeight < scrollY) {
                        scrollY = scrolledHeight;
                    }
                }
            }
        }
        else if (action == DOWN) {
            int selectedPostHeight = ((Integer) postsHeights.elementAt(selectedIndex)).intValue();
            if (selectedIndex < posts.size() - 1) {
                selectedIndex++;

                int scrolledHeight = 0;
                for (int postIndex = 0; postIndex < selectedIndex+1; postIndex++) {
                    int postHeight = ((Integer) postsHeights.elementAt(postIndex)).intValue();
                    scrolledHeight += Math.max(postHeight, MIN_POST_HEIGHT);
                }

                // Логика "умного" скролла вниз
                if (scrolledHeight > scrollY + screenHeight) {
                    scrollY = scrolledHeight - screenHeight;
                }
            }
        }
        else if (action == FIRE) {
            // Нажатие центральной кнопки (ОК) - Лайк
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
        }

        // Обязательно вызываем перерисовку после изменений!
        repaint();
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


    private void renderPostContent(Graphics g, JSONObject post, int currentY,
                                   String[] content, Hashtable mediaHeights, int offset, int postIndex) {
        // Рисуем аватарку
        String emoji = post.getObject("author").getString("avatar");
        char[] emojiChar = emoji.toCharArray();

        String emojiId = "";
        for (int charIndex = 0; charIndex < emojiChar.length; charIndex++) {
            int charCode = emojiChar[charIndex];
            emojiId += Integer.toHexString(charCode);
        }

        ITD.log(emoji + " " + emojiId);

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
        int user_dataY = currentY + PADDING;
        g.drawString(
                displayName,
                PADDING * 2 + AVATAR_SIZE + offset,
                user_dataY,
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
                    user_dataY,
                    Graphics.TOP | Graphics.LEFT
            );
        }

        //время публикации
        int createdAt = post.getInt("createdAt");
        g.setFont(fontBold);
        g.setColor(COLOR_TEXT);
        g.drawString(
                String.valueOf(createdAt) + " секунд назад",
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
            for (int mediaIndex = 0; mediaIndex < attachments.size(); mediaIndex++) {
                JSONObject mediaInfo = attachments.getObject(mediaIndex);

                String url = mediaInfo.getString("url");
                String fileName = ITD.getFileName(url);
                ITD.log(fileName);

                if (medias.containsKey(fileName)) {
                    Image media = (Image) medias.get(fileName);
//                    Integer[] postMediaHeights = (Integer[]) mediaHeights.elementAt(postIndex);
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
                        mediaRequest.addElement(new Integer(postIndex));
                        mediasQueue.addElement(mediaRequest);

//                        Vector postsNums;
//                        if (mediaPosts.containsKey(fileName)) {
//                            postsNums = (Vector) mediaPosts.get(fileName);
//                        }
//                        else {
//                            postsNums = new Vector();
//                        }
//                        postsNums.addElement(new Integer(postIndex));
//                        mediaPosts.put(fileName, postsNums);

                        mediasQueue.notify();
                    }
                }
            }
        }
    }


    public static int heightsSum(JSONArray attachments, Hashtable mediaHeights, int mediaIndex) {
        int mediaHeightsSum = 0;
        for (int i = 0; i < mediaIndex; i++) {
            String fileName = ITD.getFileName(attachments.getObject(i).getString("url"));
            mediaHeightsSum += ((Integer) mediaHeights.get(fileName)).intValue();
        }
        return mediaHeightsSum;
    }

    private int getMediaHeight(JSONObject attachmentInfo, int mediaWidth) {
        int width = attachmentInfo.getInt("width");
        int height = attachmentInfo.getInt("height");

        float ratio = Math.max(Math.min((float) height / (float) width, MAX_MEDIA_RATIO), 1f / MAX_MEDIA_RATIO); //ограничение соотношения сторон до 1 к 3
        int mediaHeight = (int) Math.ceil(mediaWidth * ratio);

//        postMediaHeightsVector.addElement(new Integer(mediaHeight));
        return mediaHeight;
    }
}