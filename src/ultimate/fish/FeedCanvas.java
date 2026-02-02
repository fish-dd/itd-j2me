package ultimate.fish;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.*;
import java.util.Hashtable;
import java.util.Vector;

public class FeedCanvas extends Canvas {
    private JSONArray posts;
    private Vector strings = new Vector();
    private Hashtable avatars = new Hashtable();

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
    private static final int COLOR_BG = 0x000000;
    private static final int COLOR_TEXT = 0xE4E6E8;
    private static final int COLOR_SEL = 0x242424;
    private static final int COLOR_BLUE = 0x0000FF;
    private static final int MIN_POST_HEIGHT = AVATAR_SIZE + PADDING*2;

    public FeedCanvas(JSONArray posts) {
        setFullScreenMode(true);
        screenWidth = getWidth();
        screenHeight = getHeight();

        // Инициализация шрифтов
        fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        lineHeight = fontPlain.getHeight();

        this.posts = posts;

        for (int i = 0; i < posts.size(); i++) {
            JSONObject post = (JSONObject) posts.get(i);
            String[] content = split(post.getString("content"), fontPlain, screenWidth - PADDING*3 - AVATAR_SIZE);
            strings.addElement(content);
        }
    }

    // === ГЛАВНЫЙ МЕТОД ОТРИСОВКИ ===
    protected void paint(Graphics g) {
        // 1. Очистка экрана
        g.setColor(COLOR_BG);
        g.fillRect(0, 0, screenWidth, screenHeight);

        // Текущая Y-координата для рисования (с учетом скролла)
        int currentY = -scrollY;

        for (int i = 0; i < posts.size(); i++) {
            JSONObject post = (JSONObject) posts.get(i);

            // Рассчитываем высоту этого поста
            String[] content = (String[]) strings.elementAt(i);
            int postHeight = PADDING*4 + lineHeight*(content.length + 1);
            postHeight = Math.max(postHeight, MIN_POST_HEIGHT);

            // Оптимизация: Рисуем, только если пост попадает в экран
            if (currentY + postHeight > 0 && currentY < screenHeight) {
                String emoji = post.getObject("author").getString("avatar");

                // Рисуем фон выделения, если пост выбран курсором
                if (i == selectedIndex) {
                    g.setColor(COLOR_SEL);
                    g.fillRect(0, currentY, screenWidth, postHeight);
                }

                // Рисуем аватарку
                char[] emojiChar = post.getObject("author").getString("avatar").toCharArray();

                String emojiId = "";
                for (int j = 0; j < emojiChar.length; j++) {
                    int charCode = emojiChar[j];
                    emojiId += Integer.toHexString(charCode);
                }

                ITD.log(emoji);
                ITD.log(emojiId);

                Image avatar;
                if (avatars.containsKey(emojiId)) {
                    avatar = (Image) avatars.get(emojiId);
                }
                else {
                    String avatarUrl = ITD.URL + "/avatar/" + emojiId;
                    byte[] avatarRaw = ITD.rawGetRequest(avatarUrl).getBytes();

                    ITD.log(avatarRaw);

                    avatar = Image.createImage(avatarRaw, 0, avatarRaw.length);
                    avatars.put(emojiId, avatar);
                }

                g.drawImage(avatar, PADDING, currentY + PADDING, 0);

                // Рисуем Имя автора
                g.setFont(fontBold);
                g.setColor(COLOR_TEXT);
                g.drawString(post.getObject("author").getString("displayName"), PADDING * 2 + AVATAR_SIZE, currentY + PADDING, Graphics.TOP | Graphics.LEFT);

                // Рисуем Текст поста
                g.setFont(fontPlain);
                g.setColor(COLOR_TEXT);
                for (int j = 0; j < content.length; j++) {
                    g.drawString(content[j], PADDING * 2 + AVATAR_SIZE, currentY + PADDING + lineHeight * (j + 1), Graphics.TOP | Graphics.LEFT);
                }

                // Рисуем Лайки
                g.setColor(COLOR_TEXT);
                String likeStr = (post.getBoolean("isLiked") ? "♥ " : "♡ ") + post.getInt("likesCount");
                g.drawString(likeStr, PADDING * 2 + AVATAR_SIZE, currentY + PADDING + lineHeight * (content.length + 1), Graphics.TOP | Graphics.LEFT);

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
                selectedIndex--;

                int scrolledHeight = 0;
                for (int i = 0; i < selectedIndex; i++) {
                    String[] content = (String[]) strings.elementAt(i);
                    int postHeight = PADDING*4 + lineHeight*(content.length + 1);
                    scrolledHeight += Math.max(postHeight, MIN_POST_HEIGHT);
                }

                // Логика "умного" скролла вверх
                if (scrolledHeight < scrollY) {
                    scrollY = scrolledHeight;
                }
            }
        }
        else if (action == DOWN) {
            if (selectedIndex < posts.size() - 1) {
                selectedIndex++;

                int scrolledHeight = 0;
                for (int i = 0; i < selectedIndex+1; i++) {
                    String[] content = (String[]) strings.elementAt(i);
                    int postHeight = PADDING*4 + lineHeight*(content.length + 1);
                    scrolledHeight += Math.max(postHeight, MIN_POST_HEIGHT);
                }

                // Логика "умного" скролла вниз
                if (scrolledHeight > scrollY + screenHeight) {
                    scrollY = scrolledHeight - screenHeight;
                }
            }
        }
//        else if (action == FIRE) {
//            // Нажатие центральной кнопки (ОК) - Лайк
//            Post p = (Post) posts.elementAt(selectedIndex);
//            p.isLiked = !p.isLiked;
//            if (p.isLiked) p.likes++; else p.likes--;
//        }

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
}