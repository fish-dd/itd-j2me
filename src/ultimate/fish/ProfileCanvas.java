package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.Vector;

public class ProfileCanvas extends FeedCanvas {
    private JSONObject profile;

    private int headerHeight;
    private int bannerHeight;


    public ProfileCanvas(JSONObject profile, JSONArray posts, ITD midlet) {
        super(posts, midlet);
        this.profile = profile;

        Vector tempPostsHeights = new Vector();
        tempPostsHeights.addElement(new Integer(headerHeight));
        for (int i = 0; i < postsHeights.size(); i++) {
            tempPostsHeights.addElement(postsHeights.elementAt(i));
        }
        postsHeights = tempPostsHeights;

        bannerHeight = screenWidth / 3;
        headerHeight = PADDING*4 + AVATAR_SIZE + lineHeight*2 + bannerHeight;
    }


    protected void paint(final Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        drawProfileHeader(g, currentY);
        currentY += headerHeight;

        for (int elementIndex = 0; elementIndex < posts.size(); elementIndex++) {
            drawPost(g, elementIndex, elementIndex+1, currentY);
            currentY += ((Integer) postsHeights.elementAt(elementIndex+1)).intValue();
        }
    }


    private void drawProfileHeader(Graphics g, int currentY) {
        if (currentY + headerHeight > 0) {
            if (selectedIndex == 0) {
                g.setColor(COLOR_SEL);
                g.fillRect(0, currentY, screenWidth, headerHeight);
            }

            g.setColor(COLOR_TEXT);
            g.fillRect(0, currentY, screenWidth, bannerHeight);

            int userDataY = currentY + PADDING + bannerHeight;

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

            g.fillRect(PADDING*2+AVATAR_SIZE, currentY+bannerHeight+PADDING*2-2+lineHeight, 100, lineHeight);
            g.fillRect(PADDING, currentY+bannerHeight+PADDING*2+AVATAR_SIZE, 175, lineHeight);
            g.fillRect(PADDING, currentY+bannerHeight+PADDING*3+AVATAR_SIZE+lineHeight, 125, lineHeight);

            // Разделительная линия
            g.setColor(COLOR_SEL);
            g.drawLine(0, currentY + headerHeight - 1, screenWidth, currentY + headerHeight - 1);
        }
    }


    protected void keyPressed(int keyCode) { //обработка нажатий клавиш
        int action = getGameAction(keyCode);

        int selectedPostHeight = ((Integer) postsHeights.elementAt(selectedIndex)).intValue();
        int scrolledHeight = ITD.sum(postsHeights, 0, selectedIndex);

        if (action == UP) {
            onUp(selectedPostHeight, scrolledHeight);
        }
        else if (action == DOWN) {
            onDown(selectedPostHeight, scrolledHeight, posts.size() + 1);
        }
        else if (action == FIRE) {
            onFire();
        }

        // Обязательно вызываем перерисовку после изменений!
        ITD.log(scrollY);
        repaint();
    }
}