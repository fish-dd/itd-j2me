package ultimate.fish;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.Hashtable;
import java.util.Vector;

public class ProfileCanvas extends FeedCanvas {
    private String profileUrl;
    private JSONObject profile;

    static final String[] URL_PARTS = {ITD.API_URL + "/posts/user/", "?limit=", "&sort=new", "&cursor="};

    String cursor = null;
    boolean isNoMorePosts = false;

    static final int COLOR_BANNER = 0x323232;

    private Image calendarIcon;

    private int bannerHeight;
    private final String HEADER_ID = "header";


    public ProfileCanvas(ITD midlet, String profileUrl) {
        this.midlet = midlet;
        this.showSelection = !hasPointerEvents();
        this.profileUrl = profileUrl;

        setFullScreenMode(false);
        initFonts();
        setScreenSize();
        initIcons();
        initCommands();

        initAvatarLoader();
        initMediaLoader();
        initPostLoader();

        getHeaderSize();
        addHeader();

        loadPosts(profileUrl, ITD.POSTS_LIMIT, null);

        ITD.loaderSleep(); //потому что ж2ме лоудер крашится без этого
        Display.getDisplay(midlet).setCurrent(this);
    }


    void initIcons() {
        super.initIcons();
        try {
            calendarIcon = midlet.getIcon("calendar");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
    }


    void initPostLoader() {
        ITD.log("пост поток");
        postLoader = new Thread(new Runnable() {
            public void run() {
                do {
                    synchronized (postLoadNotifier) {
                        try {
                            postLoadNotifier.wait();
                        } catch (Exception e) {
                            ITD.log(String.valueOf(e));
                        }
                    }

                    loadPosts(profileUrl, ITD.POSTS_LIMIT, cursor);
                    arePostsRequested = false;
                    repaint();
                } while (!isNoMorePosts);
            }
        });

        ITD.log("пост поток запуск");
        postLoader.start();
    }


    private void getHeaderSize() {
        bannerHeight = screenWidth / 3;
        headerHeight = PADDING*4 + avatarSize + lineHeight*2 + bannerHeight;
        postsHeights.put(HEADER_ID, new Integer(headerHeight));
    }


    private void addHeader() {
        JSONObject header = new JSONObject();
        header.put("id", HEADER_ID);
        elements.addElement(header);
    }


    private void loadPosts(String profileUrl, final int postsLimit, String cursor) {
        String profileResponse = ITD.getRequest(profileUrl,  midlet.getRefreshToken());
        profile = JSON.getObject(profileResponse);

        String username = profile.getString("username");
        String postsUrl = URL_PARTS[0] + username + URL_PARTS[1] + postsLimit + URL_PARTS[2];
        if (cursor != null) postsUrl += URL_PARTS[3] + cursor;
        String postsResponse = ITD.getRequest(postsUrl, midlet.getRefreshToken());
        JSONArray posts = JSON.getObject(postsResponse).getObject("data").getArray("posts");
        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            elements.addElement(posts.get(postIndex));
        }

        if (posts.size() == 0) {
            ITD.log("Больше постов нет");
            isNoMorePosts = true;
        }
    }


    void requestPosts() {
        if (!arePostsRequested && !isNoMorePosts) {
            arePostsRequested = true;
            JSONObject lastPost = (JSONObject) elements.lastElement();
            cursor = lastPost.getString("createdAt");
            ITD.log("Курсор " + cursor);

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

        drawProfileHeader(g, currentY, selectedIndex == 0);
        currentY += headerHeight;

        for (int postIndex = 1; postIndex < elements.size(); postIndex++) {
            JSONObject element = (JSONObject) elements.elementAt(postIndex);
            boolean isSelected = selectedIndex == postIndex;
            if (isSelected) selectedY = currentY;

            drawPost(g, currentY, element, isSelected);

            currentY += ((Integer) postsHeights.get(element.getString("id"))).intValue();
        }

        elementsHeight = elementsHeightTemp;

        if (scrollY + screenHeight > elementsHeight) requestPosts();
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


    private void drawProfileHeader(Graphics g, int currentY, boolean isSelected) {
        elementsHeightTemp += headerHeight;

        if (currentY + headerHeight > 0) {
            if (isSelected) {
                g.setColor(COLOR_SEL);
                g.fillRect(0, currentY, screenWidth, headerHeight);
            }

            //баннер
            String bannerUrl = profile.getString("banner");
            if (bannerUrl != null) {
                String fileName = ITD.getFileName(bannerUrl);

                if (medias.containsKey("banner")) {
                    Image banner = (Image) medias.get("banner");
                    g.drawImage(banner, 0, currentY, Graphics.TOP | Graphics.LEFT);
                }
                else {
                    synchronized (mediasQueue) {
                        Vector mediaRequest = new Vector();

                        mediaRequest.addElement(fileName);
                        mediaRequest.addElement(new Integer(BANNER));
                        mediaRequest.addElement(HEADER_ID);

                        mediasQueue.addElement(mediaRequest);
                        mediasQueue.notify();
                    }
                }
            }
            else {
                g.setColor(COLOR_BANNER);
                g.fillRect(0, currentY, screenWidth, bannerHeight);
            }

            if (isSelected) {
                g.setColor(COLOR_SEL);
                g.drawRect(0, currentY, screenWidth-1, bannerHeight);
            }

            int userDataY = currentY + PADDING + bannerHeight;

            g.setColor(COLOR_TEXT);
            // Рисуем аватарку
            String emoji = profile.getString("avatar");
            String emojiId = getEmojiId(emoji);

            if (avatars.containsKey(emojiId)) {
                Image avatar = (Image) avatars.get(emojiId);
                g.drawImage(avatar, PADDING, userDataY, 0);
            }
            else {
                synchronized (avatarsQueue) {
                    avatarsQueue.addElement(emojiId);
                    avatarsQueue.notify();
                }
            }

            // Рисуем Имя автора
            String displayName = profile.getString("displayName");
            g.setFont(fontBold);
            g.setColor(COLOR_TEXT);
            g.drawString(
                    displayName,
                    PADDING * 2 + avatarSize,
                    userDataY,
                    Graphics.TOP | Graphics.LEFT
            );
            int nameWidth = strWidth(displayName, fontPlain);

            //галочка
            boolean isVerified = profile.getBoolean("verified");
            if (isVerified) {
                int verifiedX = Math.min(PADDING * 3 + avatarSize + nameWidth, screenWidth - iconSize - PADDING);
                g.drawImage(
                        verifiedIcon,
                        verifiedX,
                        userDataY,
                        Graphics.TOP | Graphics.LEFT
                );
            }

            //юзернейм
            String username = profile.getString("username");
            g.drawString(
                    "@"+username,
                    PADDING * 2 + avatarSize,
                    userDataY + lineHeight + PADDING - 2,
                    Graphics.TOP | Graphics.LEFT
            );

            //подписчики
            String followersString = profile.getString("followersCount") + " подписчиков";
            g.setFont(fontPlain);
            g.drawString(
                    followersString,
                    PADDING,
                    userDataY + PADDING + avatarSize,
                    Graphics.TOP | Graphics.LEFT
            );
            int followersWidth = strWidth(followersString, fontPlain);

            //подписки
            String followingString = profile.getString("followingCount") + " подписок";
            g.setFont(fontPlain);
            g.drawString(
                    followingString,
                    PADDING*3 + followersWidth,
                    userDataY + PADDING + avatarSize,
                    Graphics.TOP | Graphics.LEFT
            );

            //дата регистрации
            String createdAt = profile.getString("createdAt");
            String year = createdAt.substring(0, createdAt.indexOf('-'));
            String month = createdAt.substring(createdAt.indexOf('-')+1, createdAt.indexOf('-', createdAt.indexOf('-')+1));
            String regDate = numToMonth(month) + " " + year;
            g.drawImage(
                    calendarIcon,
                    PADDING,
                    userDataY + PADDING*2 + avatarSize + lineHeight,
                    Graphics.TOP | Graphics.LEFT
            );
            g.setFont(fontPlain);
            g.drawString(
                    regDate,
                    PADDING*2 + iconSize,
                    userDataY + PADDING*2 + avatarSize + lineHeight,
                    Graphics.TOP | Graphics.LEFT
            );

            //плейсхолдеры
//            g.fillRect(PADDING*2+AVATAR_SIZE, currentY+bannerHeight+PADDING*2-2+lineHeight, 100, lineHeight);
//            g.fillRect(PADDING, userDataY+PADDING+AVATAR_SIZE, 175, lineHeight);
//            g.fillRect(PADDING, userDataY+PADDING*2+AVATAR_SIZE+lineHeight, 125, lineHeight);

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(0, currentY + headerHeight - 1, screenWidth, currentY + headerHeight - 1);
        }
    }

    
    private String numToMonth(String monthStr) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        int month = Integer.parseInt(monthStr);
        return months[month - 1];
    }


    void likePost() {
        if (selectedIndex != 0) super.likePost();
    }
}