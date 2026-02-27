package ultimate.fish;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import java.io.*;

public class ITD extends MIDlet {
    static final boolean DEBUG = true;
    private boolean isAlreadyRunning = false;

    private Display display;

    static String[] URLS = {"http://127.0.0.1:5000", "http://192.168.31.170", "http://ultimatefish.ddns.net:5000"};
    static String URL = URLS[0];
    static String API_URL = URL + "/api";
    static String NAME = "итд";

    static int POSTS_LIMIT = 5;

    private Form startForm;

    private Command keyEnterCommand = new Command("Ввод", Command.OK, 1);
    private Form tokenForm;

    private List menuList;
    private String[] menuStrings = {"Для вас", /*"Лента клана", "Подписки",*/ "Поиск", "Уведомления", "Профиль", "Настройки"};
    //иконки
    //google material symbols, Apache License, Version 2.0
    //size 16, weight 400, grade -25, optical size 20, #E4E6E8
    private Image feedIcon;
    private Image clanIcon;
    private Image followsIcon;
    private Image searchIcon;
    private Image notificationsIcon;
    private Image profileIcon;
    private Image settingsIcon;
    private Image[] menuIcons = {feedIcon, searchIcon, notificationsIcon, profileIcon, settingsIcon};
    private CommandListener menuCmdListener;
    private Command menuSelectCmd;

    public CommandListener feedCmdListener;
    public Command backToMenuCmd;

    public Thread connectThread;
    private String refreshToken;

    private final String RECORD_STORE_NAME = "itd-db";
    private final int REFRESH_TOKEN_RECORD_ID = 1;
    private RecordStore recorder;


    protected void startApp() {
        if (isAlreadyRunning) return;
        isAlreadyRunning = true;

        display = Display.getDisplay(this);

        initCommands();

        startForm = new Form(NAME);
        display.setCurrent(startForm);

        initMenuList();

        try {
            feedIcon = getIconRes("home");
            searchIcon = getIconRes("search");
            notificationsIcon = getIconRes("notifications");
            profileIcon = getIconRes("account");
            settingsIcon = getIconRes("settings");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        startForm.append("Открытие хранилища записей...\n");
        try {
            recorder = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        startForm.append("Попытка достучаться до прокси...\n");
        String connCode = "kong";
        while (!connCode.equals("pong")) {
            try {
                connCode = getRequest(URL + "/ping");
            } catch (Exception ignored) {
                ITD.log("Ошибка теста подключения, ждём");
                startForm.append("Ошибка теста подключения, ждём...\n");
                try { Thread.sleep(5000); } catch (Exception ignored1) {}
            }
        }

        startForm.append("Инициализация экрана ввода токена...\n");
        initTokenForm();
    }


    protected void pauseApp() {}


    protected void destroyApp(boolean unconditional) {
        try {
            recorder.closeRecordStore();
        } catch (Exception ignored) {}
    }


    private void initCommands() {
        backToMenuCmd = new Command("Назад", Command.BACK, 1);
        menuSelectCmd = new Command("Открыть", Command.ITEM, 1);

        feedCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backToMenuCmd) {
                    ((FeedCanvas) displayable).stopFeed();
                    display.setCurrent(menuList);
                }
            }
        };
        menuCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command != menuSelectCmd) {
                    return;
                }

                if (menuList.isSelected(0)) {
                    initFeedCanvas();
                }
                else if (menuList.isSelected(3)) {
                    initProfileCanvas();
                }
            }
        };
    }


    static String getRequest(String url) {
        try {
            HttpConnection connection = (HttpConnection) Connector.open(url);
            connection.setRequestMethod(HttpConnection.GET);

            int code = connection.getResponseCode();
            if (code == 200) {
                InputStream inputStream = connection.openInputStream();
                InputStreamReader inputReader = new InputStreamReader(inputStream, "UTF-8");
                StringBuffer buffer = new StringBuffer();

                int answerChar;
                while ((answerChar = inputReader.read()) != -1) {
                    buffer.append((char) answerChar);
                }

                final String response = buffer.toString();
                log(response);
                return response;
            }
        }
        catch (Exception e) {
            throw new RuntimeException(String.valueOf(e));
        }
        return null;
    }


    static String getRequest(String url, String refreshToken) {
        try {
            HttpConnection connection = (HttpConnection) Connector.open(url);
            connection.setRequestMethod(HttpConnection.GET);
            connection.setRequestProperty("Cookie", "refresh_token=" + refreshToken);

            int code = connection.getResponseCode();
            log(new Integer(code));
            if (code == 200) {
                InputStream inputStream = connection.openInputStream();
                InputStreamReader inputReader = new InputStreamReader(inputStream, "UTF-8");
                StringBuffer buffer = new StringBuffer();

                int answerChar;
                while ((answerChar = inputReader.read()) != -1) {
                    buffer.append((char) answerChar);
                }

                final String response = buffer.toString();
                log(response);
                return response;
            }
        }
        catch (Exception e) {
            throw new RuntimeException(String.valueOf(e));
        }
        return null;
    }


    static InputStream rawGetRequest(String url) {
        try {
            HttpConnection connection = (HttpConnection) Connector.open(url);
            connection.setRequestMethod(HttpConnection.GET);

            int code = connection.getResponseCode();
            if (code == 200) {
                InputStream inputStream = connection.openInputStream();
                return inputStream;
            }
        }
        catch (Exception e) {
            throw new RuntimeException(String.valueOf(e));
        }
        return null;
    }


    static String postRequest(String url, byte[] data, String refreshToken) {
        String response = "";
        try {
            HttpConnection connection = (HttpConnection) Connector.open(url);
            connection.setRequestMethod(HttpConnection.POST);
            connection.setRequestProperty("Cookie", "refresh_token=" + refreshToken);

            OutputStream outputStream = connection.openOutputStream();

            outputStream.write(data);
            outputStream.flush();

            int code = connection.getResponseCode();
            if (code / 100 == 2) {
                response = connection.getResponseMessage();
                System.out.println(response);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(String.valueOf(e));
        }

        return response;
    }


    static String deleteRequest(String url, byte[] data, String refreshToken) {
        String response = "";
        try {
            HttpConnection connection = (HttpConnection) Connector.open(url);
            connection.setRequestMethod(HttpConnection.DELETE);
            connection.setRequestProperty("Cookie", "refresh_token=" + refreshToken);

            OutputStream outputStream = connection.openOutputStream();

            outputStream.write(data);
            outputStream.flush();

            int code = connection.getResponseCode();
            if (code / 100 == 2) {
                response = connection.getResponseMessage();
                System.out.println(response);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(String.valueOf(e));
        }

        return response;
    }


    static void log(Object object) {
        if (!ITD.DEBUG) return;

        System.out.println(object);
    }

    static void log(int integer) {
        if (!ITD.DEBUG) return;

        System.out.println(integer);
    }


    private void initTokenForm() {
        this.tokenForm = new Form("Вход");

        try {
            if (recorder.getNumRecords() >= 1) {
                this.refreshToken = new String(recorder.getRecord(REFRESH_TOKEN_RECORD_ID), "UTF-8");
                initFeedCanvas();
                return;
            }
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        tokenForm.append("Введите refresh-токен");

        final TextField keyInput = new TextField("Токен:", null, 128, TextField.ANY);
        tokenForm.append(keyInput);

        StringItem button = new StringItem(null, "Ввод", Item.BUTTON);
        button.setDefaultCommand(keyEnterCommand);
        ItemCommandListener commandListener = new ItemCommandListener() {
            public void commandAction(Command command, Item item) {
                tokenForm.deleteAll();
                display.setCurrent(startForm);
                startForm.append("Сохранение токена...\n");

                try {
                    refreshToken = keyInput.getString();
                    byte[] refreshTokenBytes = refreshToken.getBytes();
                    startForm.append("Запись токена в RMS...\n");
                    try {
                        RecordStore.deleteRecordStore(RECORD_STORE_NAME);
                    } catch(Exception ignored) {}
                    recorder.addRecord(refreshTokenBytes, 0, refreshTokenBytes.length);
                } catch (Exception e) { throw new RuntimeException(e.toString()); }

                startForm.append("Запуск фида...\n");
                initFeedCanvas();
            }
        };
        button.setItemCommandListener(commandListener);
        tokenForm.append(button);

        display.setCurrent(tokenForm);
    }


    private void initFeedCanvas() {
        final String url = API_URL + "/posts?limit=" + POSTS_LIMIT + "&tab=popular";
        final ITD midlet = this;

        Runnable getPostsRunnable = new Runnable() {
            public void run() {
                startForm.append("Получение постов...\n");
                String postsResponse = getRequest(url,  refreshToken);

                startForm.append("Парсинг JSON...\n");
                JSONObject json = JSON.getObject(postsResponse);
                JSONArray posts = json.getObject("data").getArray("posts");

                startForm.append("Иницализация экрана фида...\n");
                FeedCanvas feedCanvas = new FeedCanvas(posts, midlet);
                display.setCurrent(feedCanvas);
            }
        };
        startForm.append("Запуск потока...\n");
        connectThread = new Thread(getPostsRunnable);
        connectThread.start();
    }


    private void initMenuList() {
        menuList = new List("Меню", List.IMPLICIT, menuStrings, menuIcons);
        menuList.setCommandListener(menuCmdListener);
        menuList.addCommand(menuSelectCmd);
    }


    private void initProfileCanvas() {
        final String profileUrl = API_URL + "/profile";
        final ITD midlet = this;

        Runnable getPostsRunnable = new Runnable() {
            public void run() {
                String profileResponse = getRequest(profileUrl,  refreshToken);

                JSONObject profile = JSON.getObject(profileResponse);

                String username = profile.getObject("user").getString("username");
                String postsUrl = API_URL + "/posts/user/" + username + "?limit=" + POSTS_LIMIT + "&sort=new";
                String postsResponse = getRequest(postsUrl, refreshToken);
                JSONObject json = JSON.getObject(postsResponse);
                JSONArray posts = json.getObject("data").getArray("posts");

                ProfileCanvas profileCanvas = new ProfileCanvas(profile, posts, midlet);
                display.setCurrent(profileCanvas);
            }
        };
        connectThread = new Thread(getPostsRunnable);
        connectThread.start();
    }


    public String getRefreshToken() {
        return this.refreshToken;
    }


    public static int sum(Integer[] numArray, int start, int end) {
        int numSum = 0;
        for (int i = start; i < end; i++) {
            numSum = numSum + numArray[i].intValue();
        }
        return numSum;
    }


    public static String getFileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public static int toInt(Object num) {
        return ((Integer) num).intValue();
    }

    public static Image getIconRes(String path) throws IOException {
        return Image.createImage(Class.class.getResourceAsStream("/" + path + ".png"));
    }
}
