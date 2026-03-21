package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.Vector;

public class ProfileCanvas extends FeedCanvas {
    private JSONObject profile;

    static final int COLOR_BANNER = 0x323232;

    private Image calendarIcon;

    private int bannerHeight;
    private final String HEADER_ID = "header";


    public ProfileCanvas(JSONObject profile, JSONArray posts, ITD midlet) {
        super(posts, midlet);
        this.profile = profile;

        try {
            calendarIcon = ITD.getPNGRes("calendar");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        bannerHeight = screenWidth / 3;
        headerHeight = PADDING*4 + AVATAR_SIZE + lineHeight*2 + bannerHeight;
        postsHeights.put(HEADER_ID, new Integer(headerHeight));

        JSONObject header = new JSONObject();
        header.put("id", HEADER_ID);

        elements = new Vector();
        elements.addElement(header);
        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            elements.addElement(posts.get(postIndex));
        }
    }


    protected void paint(final Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

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
    }


    private void drawProfileHeader(Graphics g, int currentY, boolean isSelected) {
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
                    PADDING * 2 + AVATAR_SIZE,
                    userDataY,
                    Graphics.TOP | Graphics.LEFT
            );
            int nameWidth = strWidth(displayName, fontPlain);

            //галочка
            boolean isVerified = profile.getBoolean("verified");
            if (isVerified) {
                int verifiedX = Math.min(PADDING * 3 + AVATAR_SIZE + nameWidth, screenWidth - ICON_SIZE - PADDING);
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
                    PADDING * 2 + AVATAR_SIZE,
                    userDataY + lineHeight + PADDING - 2,
                    Graphics.TOP | Graphics.LEFT
            );

            //подписчики
            String followersString = profile.getString("followersCount") + " подписчиков";
            g.setFont(fontPlain);
            g.drawString(
                    followersString,
                    PADDING,
                    userDataY + PADDING + AVATAR_SIZE,
                    Graphics.TOP | Graphics.LEFT
            );
            int followersWidth = strWidth(followersString, fontPlain);

            //подписки
            String followingString = profile.getString("followingCount") + " подписок";
            g.setFont(fontPlain);
            g.drawString(
                    followingString,
                    PADDING*3 + followersWidth,
                    userDataY + PADDING + AVATAR_SIZE,
                    Graphics.TOP | Graphics.LEFT
            );

            //дата регистрации
            String createdAt = profile.getString("createdAt");
            String year = createdAt.substring(0, createdAt.indexOf('-'));
            String month = createdAt.substring(createdAt.indexOf('-')+1, createdAt.indexOf('-', createdAt.indexOf('-')+1));
            String regDate = year + "." + month;
            g.drawImage(
                    calendarIcon,
                    PADDING,
                    userDataY + PADDING*2 + AVATAR_SIZE + lineHeight,
                    Graphics.TOP | Graphics.LEFT
            );
            g.setFont(fontPlain);
            g.drawString(
                    regDate,
                    PADDING*2 + ICON_SIZE,
                    userDataY + PADDING*2 + AVATAR_SIZE + lineHeight,
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


    void likePost() {
        if (selectedIndex != 0) super.likePost();
    }
}