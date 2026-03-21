package ultimate.fish;

//нектар шиновона, межгалактический респект
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import java.io.*;
import java.util.Vector;

public class ITD extends MIDlet {
    static final boolean DEBUG = true;
    private boolean isAlreadyRunning = false;
    private String appVersion;

    private Display display;

    static String[] URLS = {"http://127.0.0.1:5000", "http://192.168.31.170", "http://ultimatefish.ddns.net:5000"};
    static String URL = URLS[0];
    static String API_URL = URL + "/api";
    static String NAME = "итд";

    static int POSTS_LIMIT = 5;
    static int J2ME_LOADER_FIX_SLEEP = 200;

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
    private Image[] menuIcons;
    private CommandListener menuCmdListener;
    public Command selectCmd;

    private CommandListener aboutCmdListener;
    private Command aboutCmd;

    public CommandListener feedCmdListener;
    public Command backToMenuCmd;
    public Command likeCmd;

    public CommandListener settingsCmdListener;

    public Thread connectThread;
    private String refreshToken;

    private final String REFRESH_TOKEN_RECORD_STORE_NAME = "itd-db";
    private RecordStore refreshTokenRec;
    private final String SETTINGS_RECORD_STORE_NAME = "itd-db";
    private RecordStore settingsRec;


    protected void startApp() {
        if (isAlreadyRunning) return;
        isAlreadyRunning = true;

        appVersion = getAppProperty("MIDlet-Version");

        display = Display.getDisplay(this);

        initCommands();

        startForm = new Form(NAME);
        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(startForm);

        try {
            feedIcon = getIconRes("home");
            searchIcon = getIconRes("search");
            notificationsIcon = getIconRes("notifications");
            profileIcon = getIconRes("account");
            settingsIcon = getIconRes("settings");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
        menuIcons = new Image[]{feedIcon, searchIcon, notificationsIcon, profileIcon, settingsIcon};

        initMenuList();

        if (!getAppProperty("MIDlet-Vendor").equals("ultimate_fish")) {
            throw new RuntimeException();
        }

        startForm.append("Открытие хранилища записей...\n");
        try {
            refreshTokenRec = RecordStore.openRecordStore(REFRESH_TOKEN_RECORD_STORE_NAME, true);
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
            refreshTokenRec.closeRecordStore();
        } catch (Exception ignored) {}
    }


    private void initCommands() {
        backToMenuCmd = new Command("Назад", Command.BACK, 1);
        selectCmd = new Command("Открыть", Command.OK, 1);
        aboutCmd = new Command("О программе", Command.HELP, 2);
        likeCmd = new Command("Лайк", Command.ITEM, 1);

        feedCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == likeCmd) {
                    FeedCanvas feed = ((FeedCanvas) displayable);
                    feed.likePost();
                }
                else if (command == selectCmd) {
                    display.setCurrent(new Alert(":(", "Ещё не реализовано", null, null));
                }
                else if (command == backToMenuCmd) {
                    ((FeedCanvas) displayable).stopFeed();
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(menuList);
                }
            }
        };
        menuCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == aboutCmd) {
                    initAboutForm();
                }
                else if (command == selectCmd) {
                    if (menuList.isSelected(0)) {
                        initFeedCanvas();
                    }
                    else if (menuList.isSelected(3)) {
                        initProfileCanvas();
                    }
                    else if (menuList.isSelected(4)) {
                        initSettingsForm();
                    }
                }
            }
        };
        settingsCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backToMenuCmd) {
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(menuList);
                }
            }
        };
        aboutCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backToMenuCmd) {
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(menuList);
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
            log("Ошибка getRequest " + e);
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
            log("Ошибка getRequest " + e);
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
            log("Ошибка rawGetRequest " + e);
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
            log("Ошибка postRequest " + e);
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
            log("Ошибка deleteRequest " + e);
        }

        return response;
    }


    static void log(Object object) {
        if (ITD.DEBUG) System.out.println(object);
    }


    static void log(int integer) {
        if (!ITD.DEBUG) System.out.println(integer);
    }


    static void log(boolean bool) {
        if (!ITD.DEBUG) System.out.println(bool);
    }


    private void initTokenForm() {
        this.tokenForm = new Form("Вход");

        try {
            if (refreshTokenRec.getNumRecords() >= 1) {
                this.refreshToken = new String(refreshTokenRec.getRecord(1), "UTF-8");

                for (int i = 0; i < 3; i++) {
                    startForm.append("Проверка токена...\n");

                    boolean isTokenValid = getRequest(URL + "/valid", refreshToken).equals("true");
                    if (isTokenValid) {
                        initFeedCanvas();
                        return;
                    }
                }

                tokenForm.append("Кажется, токен недействителен.\n");
            }
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        tokenForm.append("Введите refresh-токен.\n" +
                "Он находится в cookie браузера");

        final TextField keyInput = new TextField("Токен:", null, 128, TextField.ANY);
        tokenForm.append(keyInput);

        StringItem button = new StringItem(null, "Ввод", Item.BUTTON);
        button.setDefaultCommand(keyEnterCommand);
        ItemCommandListener commandListener = new ItemCommandListener() {
            public void commandAction(Command command, Item item) {
                tokenForm.deleteAll();
                loaderSleep(); //потому что ж2ме лоудер крашится без этого
                display.setCurrent(startForm);
                startForm.append("Сохранение токена...\n");

                try {
                    refreshToken = keyInput.getString();
                    byte[] refreshTokenBytes = refreshToken.getBytes();
                    startForm.append("Запись токена в RMS...\n");
                    try {
                        RecordStore.deleteRecordStore(REFRESH_TOKEN_RECORD_STORE_NAME);
                    } catch(Exception ignored) {}
                    refreshTokenRec.addRecord(refreshTokenBytes, 0, refreshTokenBytes.length);
                } catch (Exception e) { throw new RuntimeException(e.toString()); }

                startForm.append("Запуск фида...\n");
                initFeedCanvas();
            }
        };
        button.setItemCommandListener(commandListener);
        tokenForm.append(button);

        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(tokenForm);
    }


    private void initFeedCanvas() {
        final String url = API_URL + "/posts?limit=" + POSTS_LIMIT + "&tab=popular";
        final ITD midlet = this;

        Runnable getPostsRunnable = new Runnable() {
            public void run() {
                try {
                    startForm.append("Получение постов...\n");
                    String postsResponse = getRequest(url, refreshToken);

                    startForm.append("Парсинг JSON...\n");
                    JSONObject json = JSON.getObject(postsResponse);
                    JSONArray posts = json.getObject("data").getArray("posts");

                    startForm.append("Иницализация экрана фида...\n");
                    FeedCanvas feedCanvas = new FeedCanvas(posts, midlet);
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(feedCanvas);
                }
                catch (Exception ignored) {startForm.append("Произошла ошибка");}
            }
        };
        startForm.append("Запуск потока...\n");
        connectThread = new Thread(getPostsRunnable);
        connectThread.start();
    }


    private void initMenuList() {
        menuList = new List("Меню", List.IMPLICIT, menuStrings, menuIcons);
        menuList.setCommandListener(menuCmdListener);
        menuList.addCommand(selectCmd);
        menuList.addCommand(aboutCmd);
    }


    private void initProfileCanvas() {
        final String profileUrl = API_URL + "/users/me";
        final ITD midlet = this;

        Runnable getPostsRunnable = new Runnable() {
            public void run() {
                String profileResponse = getRequest(profileUrl,  refreshToken);

                JSONObject profile = JSON.getObject(profileResponse);

                String username = profile.getString("username");
                String postsUrl = API_URL + "/posts/user/" + username + "?limit=" + POSTS_LIMIT + "&sort=new";
                String postsResponse = getRequest(postsUrl, refreshToken);
                JSONObject json = JSON.getObject(postsResponse);
                JSONArray posts = json.getObject("data").getArray("posts");

                ProfileCanvas profileCanvas = new ProfileCanvas(profile, posts, midlet);
                loaderSleep(); //потому что ж2ме лоудер крашится без этого
                display.setCurrent(profileCanvas);
            }
        };
        connectThread = new Thread(getPostsRunnable);
        connectThread.start();
    }


    private void initSettingsForm() {
        Form settingsForm = new Form("Настройки");
        settingsForm.setCommandListener(settingsCmdListener);
        settingsForm.addCommand(backToMenuCmd);
        settingsForm.append("Тут будут настройки");
        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(settingsForm);
    }


    private void initAboutForm() {
        Form aboutForm = new Form("О программе");

        aboutForm.setCommandListener(aboutCmdListener);
        aboutForm.addCommand(backToMenuCmd);

        StringItem title = new StringItem(null, "итд J2ME v" + appVersion + "\n");
        title.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        title.setLayout(Item.LAYOUT_CENTER);
        aboutForm.append(title);

        aboutForm.append("by ultimate_fish\n");

        Image logoImg = null;
        try { logoImg = getIconRes("itd"); } catch (IOException ignored) {}
        ImageItem logo = new ImageItem(null, logoImg, Item.LAYOUT_CENTER, "Логотип");
        aboutForm.append(logo);

        StringItem description = new StringItem(null,
                "Клиент молодёжной соц. сети для несвежих телефонов. Скорее всего ваш 2G тапок дороже 600 рублей его запустит (уже запустил).\n" +
                "\n" +
                "Использованные компоненты:\n" +
                " • NNJSON — github.com/shinovon/nnjson — лицензия MIT\n" +
                " • Material Symbols — fonts.google.com/icons — лицензия Apache 2.0\n" +
                "\n" +
                "Отдельное спасибо:\n" +
                " • shinovon и nnproject за NNJSON, KEmulator nnmod и вдохновение\n" +
                " • azukicatisreal и vip0ll за то, что запихали в J2ME\n" +
                " • Бесчисленным добрякам, пилящим гайды\n" +
                " • Километрам документаций");
        description.setLayout(Item.LAYOUT_LEFT);
        aboutForm.append(description);

        loaderSleep(); //потому что j2me loader что? правильно
        display.setCurrent(aboutForm);
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


    public static int sum(Vector numVector, int start, int end) {
        int numSum = 0;
        for (int i = start; i < end; i++) {
            numSum = numSum + ((Integer) numVector.elementAt(i)).intValue();
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


    private static void loaderSleep() {
        try {
            Thread.sleep(J2ME_LOADER_FIX_SLEEP);
        } catch (InterruptedException ignored) {}
    }
}
