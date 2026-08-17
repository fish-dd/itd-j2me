package ultimate.fish;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.Hashtable;

public class PostCanvas extends FeedCanvas {
    boolean kolbasa = true;

    final String postId;
    JSONObject post;

    Hashtable elementsHeights = postsHeights;
//    Hashtable postsHeights = elementsHeights;
    Hashtable commentsStrings = new Hashtable();

    final FeedCanvas targetScreen;

    Thread commentLoader;
    final Object commentLoadNotifier = new Object();
    boolean areCommentsRequested = false;

    int totalComments = -1;

    PostCanvas(ITD midlet, JSONObject post, FeedCanvas targetScreen) {
        super();
        this.midlet = midlet;
        this.post = post;
        this.postId = post.getString("id");
        this.targetScreen = targetScreen;

        setFullScreenMode(false);
        setTitle("Пост");
        initFonts();
        setScreenSize();
        initIcons();

        initAvatarLoader();
        initMediaLoader();
        initCommentLoader();
        initCommands();

//        loadPost();
        elements.addElement(post);

        ITD.log("пост стартовал");
    }


    void putMedia(String id, Image image, Integer height) {
        medias.put(id, image);
        elementsHeights.put(id, height);
    }


    JSONObject loadPost() {
        String url = ITD.API_URL + "/posts/" + postId;

        String response = ITD.getRequest(url, midlet.getRefreshToken(), true);
        post = JSON.getObject(response);

        return post;
    }


    void loadComments(int limit) {
        String url = ITD.API_URL + "/posts/" + postId + "/comments?sort=popular&limit=" + limit;

        String response = ITD.getRequest(url, midlet.getRefreshToken(), true);
        JSONObject jsonData = JSON.getObject(response).getObject("data");
        totalComments = jsonData.getInt("total");
        JSONArray comments = jsonData.getArray("comments");
        for (int commentIndex = 0; commentIndex < comments.size(); commentIndex++) {
            elements.addElement(comments.getObject(commentIndex));
        }
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
        }

        elementsHeightTemp = 0;

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            JSONObject element = (JSONObject) elements.elementAt(elementIndex);
            boolean isSelected = selectedIndex == elementIndex;
            if (isSelected) selectedY = currentY;

            if (elementIndex == 0) {
                drawPost(g, currentY, post, isSelected);
            }
            else {
                drawComment(g, currentY, element, isSelected);
            }
            // Сдвигаем курсор рисования вниз
            currentY += ((Integer) elementsHeights.get(element.getString("id"))).intValue();
        }

        elementsHeight = elementsHeightTemp;

        if ((scrollY + screenHeight >= elementsHeight) && (elements.size() - 1 != totalComments)) requestComments();
        if (areCommentsRequested) {
            String notification = "Прогрузка комментов...";
            g.setColor(COLOR_DATA_REQUEST_NOTIFY);
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


    int getCommentHeight(JSONObject comment) {
        String id = comment.getString("id");

        if (elementsHeights.containsKey(id)) {
            return ((Integer) elementsHeights.get(id)).intValue();
        }

        int commentHeight = calcCommentHeight(comment);
        elementsHeights.put(id, new Integer(commentHeight));
        return commentHeight;
    }


    int calcCommentHeight(JSONObject comment) {
        return fontPlain.getHeight() + 3;
    }

    //ща по другому попробую
//    protected int calcPostHeight(JSONObject post) {
//        int height = super.calcPostHeight(post);
//        String id = post.getString("id");
//        elementsHeights.put()
//        return height;
//    }


    void drawComment(Graphics g, int currentY, JSONObject comment, boolean isSelected) {
        final String id = comment.getString("id");

        String[] content;
        if (commentsStrings.contains(id)) {
            content = (String[]) commentsStrings.get(id);
        }
        else {
            content = split(comment.getString("content"), fontPlain, screenWidth - PADDING*2);
            commentsStrings.put(id, content);
        }

        int commentHeight = getCommentHeight(comment);
        elementsHeightTemp += commentHeight;

        // Оптимизация: Рисуем, только если коммент попадает в экран
        if (currentY + commentHeight > 0 && currentY < screenHeight) {
            // Рисуем фон выделения, если коммент выбран курсором
            if (isSelected && showSelection) {
                g.setColor(COLOR_SEL);
                g.fillRect(0, currentY, screenWidth, commentHeight);
            }
            //чтобы после перехода с сенсора на кнопки выделение было на комменте посреди экрана:
            else if (!showSelection && -currentY + screenHeight/2 <= commentHeight && currentY <= screenHeight/2){
                selectedIndex = elements.indexOf(comment);
            }

            //содержимое коммента
            g.setColor(COLOR_TEXT);
            g.drawString(
                    comment.getString("content"),
                    PADDING,
                    currentY + 1,
                    Graphics.TOP | Graphics.LEFT
            );

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(0, currentY + commentHeight - 1, screenWidth - 1, currentY + commentHeight - 1);
        }
    }


    void likePost() {
        likePost(post);
    }


    void commentPost() {
        commentPost(post);
    }


    void repostPost() {
        repostPost(post);
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
