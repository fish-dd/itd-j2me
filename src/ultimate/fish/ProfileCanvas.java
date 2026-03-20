package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.Vector;

public class ProfileCanvas extends FeedCanvas {
    private JSONObject profile;

    private int bannerHeight;


    public ProfileCanvas(JSONObject profile, JSONArray posts, ITD midlet) {
        super(posts, midlet);
        this.profile = profile;

        JSONObject header = new JSONObject();
        header.put("id", "header");

        elements = new Vector();
        elements.addElement(new JSONObject());
        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            elements.addElement(posts.get(postIndex));
        }

        bannerHeight = screenWidth / 3;
        headerHeight = PADDING*4 + AVATAR_SIZE + lineHeight*2 + bannerHeight;
        postsHeights.put("header", new Integer(headerHeight));
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

            drawPost(g, currentY, element, isSelected);

            if (isSelected) selectedY = currentY;
            currentY += ((Integer) postsHeights.get(element.getString("id"))).intValue();
        }
    }


    private void drawProfileHeader(Graphics g, int currentY, boolean isSelected) {
        if (currentY + headerHeight > 0) {
            if (isSelected) {
                g.setColor(COLOR_SEL);
                g.fillRect(0, currentY, screenWidth, headerHeight);
            }

            g.setColor(COLOR_TEXT);
            g.fillRect(0, currentY, screenWidth, bannerHeight);

            g.setColor(COLOR_BG);
            g.setFont(fontBold);
            g.drawString("Заготовка", 0, currentY, Graphics.TOP | Graphics.LEFT);

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

        int selectedPostHeight = selectedIndex == 0 ? headerHeight : getPostHeight((JSONObject) elements.elementAt(selectedIndex));
        int scrolledHeight = selectedY - 1 + selectedPostHeight;

        if (action == UP) {
            onUp(selectedPostHeight, scrolledHeight);
        }
        else if (action == DOWN) {
            onDown(selectedPostHeight, scrolledHeight, elements.size());
        }

        // Обязательно вызываем перерисовку после изменений!
        ITD.log(scrollY);
        repaint();
    }


    void likePost() {
        if (selectedIndex != 0) super.likePost();
    }
}