package ultimate.fish;

import cc.nnproject.json.JSONObject;

import javax.microedition.lcdui.Canvas;
import java.util.Vector;

public abstract class ScrollableCanvas extends Canvas {
    Vector elements = new Vector();
    int elementsHeight;

    // Параметры UI
    int scrollY = 0;         // Смещение прокрутки по вертикали
    int selectedY = 0;
    int selectedIndex = 0;   // Индекс выбранного поста
    int screenWidth;
    int screenHeight;

    static final double SCROLL_SLOWDOWN_COEF = 0.8;
    static final double SCROLL_THRESHOLD = 0.7;
    static final double SCROLL_VELOCITY = 50;
    static final double SCROLL_END = 2;
    static final float SCROLL_MAX_COEF = 5f;
    static final int SCROLL_OVERFLOW = 16;
    static final int SCROLL_FRAMERATE = 30;
    static final int SCROLL_MIN_MOVE = 80;
    static final int SCROLL_HEIGHT = 100;

    Thread scrollThread = new Thread();
    boolean showSelection;
    boolean isPressed = false;
    boolean isDragged = false;
    int touchY;
    int prevTouchY;
    int startTouchY;
    long startTouchTime;


    ScrollableCanvas() {
        this.showSelection = !hasPointerEvents();
    }


    protected void keyPressed(int keyCode) { //обработка нажатий клавиш
        if (!showSelection) {
            showSelection = true;
            addNontouchCmds();
            repaint();
            return;
        }

        int action = getGameAction(keyCode);

        int selectedElementHeight = getElementHeight((JSONObject) elements.elementAt(selectedIndex));
        int scrolledHeight = scrollY + selectedY;

        if (action == UP) {
            onUp(selectedElementHeight, scrolledHeight);
        }
        else if (action == DOWN) {
            onDown(selectedElementHeight, scrolledHeight, elements.size());
        }

        // Обязательно вызываем перерисовку после изменений!
        ITD.log(scrollY);
        repaint();
    }


    void onUp(int selectedElementHeight, int scrolledHeight) {
        if (selectedIndex > 0) {
            int prevPostHeight = getElementHeight((JSONObject) elements.elementAt(selectedIndex - 1));

            // Логика "умного" скролла вверх
            if (selectedElementHeight > screenHeight) {
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
                    scrollY = Math.max(Math.min(scrolledHeight - prevPostHeight, scrolledHeight + selectedElementHeight - screenHeight), 0);
                    ITD.log("scroll up state 6");
                }
            }
            ITD.log(selectedElementHeight);
        }
        else if (selectedElementHeight > screenHeight) {
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


    void onDown(int selectedElementHeight, int scrolledHeight, int elementsAmount) {
        if (selectedIndex < elementsAmount - 1) { // если не последний пост
            int nextPostHeight = getElementHeight((JSONObject) elements.elementAt(selectedIndex + 1));

            // Логика "умного" скролла вниз
            if (selectedElementHeight > screenHeight) { // если текущий пост выше чем экран
                if (scrollY + screenHeight == scrolledHeight + selectedElementHeight) { // если самый конец поста
                    if (nextPostHeight > screenHeight) { // если следующий пост выше экрана
                        selectedIndex++;
                        scrollY = scrolledHeight + selectedElementHeight; // прокрутить в самое начало следующего поста
                        ITD.log("scroll down state 1");
                    }
                    else {
                        selectedIndex++;
                        scrollY = scrolledHeight + selectedElementHeight + nextPostHeight - screenHeight; // прокрутить в конец следующего поста
                        ITD.log("scroll down state 2");
                    }
                }
                else if (scrolledHeight + selectedElementHeight - (scrollY + screenHeight) < SCROLL_HEIGHT) { // если до низа поста осталось меньше чем значение прокрутки
                    scrollY = scrolledHeight + selectedElementHeight - screenHeight; // докрутить до конца поста
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
                    scrollY = scrolledHeight + selectedElementHeight; // прокрутить в начало следующего поста
                    ITD.log("scroll down state 5");
                }
                else {
                    selectedIndex++;
                    scrollY = Math.max(scrolledHeight + selectedElementHeight + nextPostHeight - screenHeight, 0); // прокрутить в конец следующего поста, ограничение чтобы не уезжать за верх
                    ITD.log("scroll down state 6");
                }
            }
            ITD.log(selectedElementHeight);
        }
        else {
            if (selectedElementHeight > screenHeight) { // если последний пост и он выше чем экран
                if (scrolledHeight + selectedElementHeight - (scrollY + screenHeight) < SCROLL_HEIGHT) { // если до низа поста осталось меньше чем значение прокрутки
                    scrollY = scrolledHeight + selectedElementHeight - screenHeight; // докрутить до конца поста
                    ITD.log("scroll down state 7");
                }
                else {
                    scrollY += SCROLL_HEIGHT; // прокрутить на значение прокрутки вниз
                    ITD.log("scroll down state 8");
                }
            }
        }
    }


    protected abstract int getElementHeight(Object object);


    protected void pointerPressed(int x, int y) {
        ITD.log("НАЖАТИЕ " + x + " " + y);
        scrollThread.interrupt();
        isPressed = true;
        removeNontouchCmds();
        showSelection = false;
    }


    protected void pointerDragged(int x, int y) {
        isPressed = false;
        if (!isDragged) {
            scrollThread.interrupt();
            isDragged = true;
            removeNontouchCmds();
            showSelection = false;
            touchY = prevTouchY = startTouchY = y;
            startTouchTime = System.currentTimeMillis();
        }

        scrollY -= y - touchY;
        scrollY = Math.min(Math.max(scrollY, 0), elementsHeight + SCROLL_OVERFLOW - screenHeight);

        int dPrev = prevTouchY - touchY;
        int dNow = touchY - y;
        if (Math.abs(dPrev) + Math.abs(dNow) != Math.abs(dPrev + dNow)) {
            touchY = prevTouchY = startTouchY = y;
            startTouchTime = System.currentTimeMillis();
        }

        prevTouchY = touchY;
        touchY = y;
        repaint();
    }


    protected void pointerReleased(int x, int y) {
        ITD.log("ОТПУСТИЕ НАЖАТИЯ " + x + " " + y);
        if (isDragged) {
            isDragged = false;

            int deltaTouchY = startTouchY - y;
            long deltaTime = System.currentTimeMillis() - startTouchTime;
            float velocityCoef = (float)deltaTouchY / deltaTime;
            if (Math.abs(velocityCoef) > SCROLL_THRESHOLD && Math.abs(deltaTouchY) > SCROLL_MIN_MOVE) {
                float scrollMaxCoef = velocityCoef >= 0 ? SCROLL_MAX_COEF : -SCROLL_MAX_COEF;
                velocityCoef = Math.abs(velocityCoef) > SCROLL_MAX_COEF ? scrollMaxCoef : velocityCoef;
//                setTitle(Math.abs(velocityCoef) + "/" + Math.abs(deltaTouchY) + "/" + Math.abs(deltaTime));
                inertialScroll(velocityCoef);
            }
        }
        else {
            isPressed = false;
            hitBoxesCheck(x, y);
        }
    }


    void inertialScroll(final float coef) {
        scrollThread.interrupt();
        scrollThread = new Thread(new Runnable() {
            public void run() {
                float velocity = (float)SCROLL_VELOCITY * coef;
                while (Math.abs(velocity) > SCROLL_END) {
                    scrollY = Math.min(Math.max(scrollY + (int) velocity, 0), elementsHeight + SCROLL_OVERFLOW - screenHeight);
                    if (scrollY == 0 || scrollY == elementsHeight + SCROLL_OVERFLOW - screenHeight) break;

                    velocity *= (float) SCROLL_SLOWDOWN_COEF;

                    repaint();
                    try { Thread.sleep(1000/SCROLL_FRAMERATE); }
                    catch (Exception ignored) { break; }
                }
            }
        });
        scrollThread.start();
    }


    protected abstract void addNontouchCmds();


    protected abstract void removeNontouchCmds();


    protected abstract void hitBoxesCheck(int x, int y);
}
