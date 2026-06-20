package ultimate.fish;

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

    static String[] URLS = {"http://127.0.0.1:5000", "http://192.168.31.170", "http://ultimatefish.ddns.net:1740", "http://2.26.98.34:1740"};
    static String URL = URLS[2];
    static String API_URL = URL + "/api";
    static String NAME = "итд";

    static int POSTS_LIMIT = 5;
    static int J2ME_LOADER_FIX_SLEEP = 100;

    private Form startForm;

    private ItemCommandListener startCmdListener;
    private Command keyEnterCommand;
    private Command keyRetryCommand;
    private TextField keyInput;
    private Form tokenForm;

    private List menuList;
    private String[] menuStrings = {"Для вас", /*"Лента клана", "Подписки",*/ "Поиск", "Новый пост", "Уведомления", "Профиль", "Настройки"};
    //иконки
    //google material symbols, Apache License, Version 2.0
    //size 16, weight 400, grade -25, optical size 20, #E4E6E8
    //size 32, weight 400, grade -25, optical size 40, #E4E6E8
    private Image feedIcon;
    private Image clanIcon;
    private Image followsIcon;
    private Image searchIcon;
    private Image plusIcon;
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
    public Command repostCmd;

    public CommandListener writerCmdListener;
    public Command postCmd;

    public CommandListener settingsCmdListener;

    private String refreshToken = null;

    private final String REFRESH_TOKEN_RECORD_STORE_NAME = "itd-db";
    private RecordStore refreshTokenRec;
    private final String SETTINGS_RECORD_STORE_NAME = "itd-settings";
    private RecordStore settingsRec;

    //связанное с масштабом
    public static final int[][] FONT_THRESHOLD = {{0, 16, 32}, {17, 32, 64}};
    public int iconSize;
    public int avatarSize;


    protected void startApp() {
        if (isAlreadyRunning) return;
        isAlreadyRunning = true;

        appVersion = getAppProperty("MIDlet-Version");

        display = Display.getDisplay(this);
        setIconSize();

        initCommands();

        startForm = new Form(NAME);
        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(startForm);

        try {
            feedIcon = getIcon("home");
            searchIcon = getIcon("search");
            plusIcon = getIcon("plus");
            notificationsIcon = getIcon("notifications");
            profileIcon = getIcon("account");
            settingsIcon = getIcon("settings");
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
        menuIcons = new Image[]{feedIcon, searchIcon, plusIcon, notificationsIcon, profileIcon, settingsIcon};

        initMenuList();

        if (!getAppProperty("MIDlet-Vendor").equals("ultimate_fish")) {
            throw new RuntimeException();
        }

        startPrintln("Открытие хранилища записей...");
        try {
            refreshTokenRec = RecordStore.openRecordStore(REFRESH_TOKEN_RECORD_STORE_NAME, true);
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        startPrintln("Попытка достучаться до прокси...");
        String connCode;
        while (true) {
            connCode = getRequest(URL + "/ping");

            if (connCode != null && connCode.equals("pong")) break;

            ITD.log("Ошибка теста подключения, ждём");
            startPrintln("Ошибка теста подключения, ждём...");
            try { Thread.sleep(5000); } catch (Exception ignored1) {}
        }

        startPrintln("Инициализация экрана ввода токена...");
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
        selectCmd = new Command("Открыть", Command.ITEM, 1);
        aboutCmd = new Command("О программе", Command.SCREEN, 2);
        likeCmd = new Command("Лайк", Command.ITEM, 1);
        repostCmd = new Command("Репост", Command.ITEM, 2);
        keyEnterCommand = new Command("Ввод", Command.OK, 1);
        keyRetryCommand = new Command("Повторить", Command.OK, 2);
        postCmd = new Command("Опубликовать", Command.OK, 1);

        feedCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == likeCmd) {
                    FeedCanvas feed = ((FeedCanvas) displayable);
                    feed.likePost();
                }
                else if (command == repostCmd) {
                    FeedCanvas feed = ((FeedCanvas) displayable);
                    feed.repostPost();
                }
                else if (command == selectCmd) {
                    display.setCurrent(new Alert(":(", "Ещё не реализовано", null, null));
                }
                else if (command == backToMenuCmd) {
                    display.setCurrent(menuList);
                    ((FeedCanvas) displayable).stopFeed();
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
                    else if (menuList.isSelected(2)) {
                        initWriter();
                    }
                    else if (menuList.isSelected(4)) {
                        initProfileCanvas();
                    }
//                    else if (menuList.isSelected(5)) {
//                        initSettingsForm();
//                    }
                    else {
                        display.setCurrent(new Alert("Ещё не реализовано", ":(", null, AlertType.WARNING));
                    }
                }
            }
        };
        initSettingsCmds();
        aboutCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backToMenuCmd) {
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(menuList);
                }
            }
        };
        startCmdListener = new ItemCommandListener() {
            public void commandAction(Command command, Item item) {
                display.setCurrent(startForm);

                if (command == keyEnterCommand) {
                    tokenForm.deleteAll();

                    startPrintln("Сохранение токена...");

                    try {
                        refreshToken = keyInput.getString();
                        byte[] refreshTokenBytes = refreshToken.getBytes();

                        startPrintln("Запись токена в RMS...");

                        refreshTokenRec.closeRecordStore();
                        RecordStore.deleteRecordStore(REFRESH_TOKEN_RECORD_STORE_NAME);
                        refreshTokenRec = RecordStore.openRecordStore(REFRESH_TOKEN_RECORD_STORE_NAME, true);

                        refreshTokenRec.addRecord(refreshTokenBytes, 0, refreshTokenBytes.length);
                    } catch (Exception e) {
                        throw new RuntimeException(e.toString());
                    }
                }

                initTokenForm();
            }
        };
        writerCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                Writer writer = (Writer) displayable;
                Displayable targetScreen = writer.getTargetScreen();

                if (command == postCmd) {
                    int type = writer.getType();
                    String text = writer.getString();

                    if (text.length() == 0) {
                        display.setCurrent(new Alert("Пустой пост", null, null, AlertType.ERROR));
                    }

                    Alert sendingSplash = new Alert("Публикация...", null, null, AlertType.INFO);
                    sendingSplash.setTimeout(Alert.FOREVER);
                    sendingSplash.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
                    display.setCurrent(sendingSplash);

                    String url = null;
                    String content = null;
                    if (type == Writer.SELF) {
                        url = API_URL + "/posts";

                        JSONObject jsonContent = new JSONObject();
                        jsonContent.put("content", text);
                        content = jsonContent.toString();
                    }
                    else if (type == Writer.REPOST) {
                        String postId = writer.getPostId();

                        url = API_URL + "/posts/" + postId + "/repost";

                        JSONObject jsonContent = new JSONObject();
                        jsonContent.put("content", text);
                        content = jsonContent.toString();

                        if (targetScreen instanceof FeedCanvas) {
                            FeedCanvas targetScreenFC = (FeedCanvas) targetScreen;

                            for (int elementIndex = 0; elementIndex < targetScreenFC.elements.size(); elementIndex++) {
                                JSONObject element = (JSONObject) targetScreenFC.elements.elementAt(elementIndex);
                                if (element.getString("id").equals(postId)) {
                                    int repostsCount = element.getInt("repostsCount");
                                    element.put("repostsCount",repostsCount + 1);
                                    targetScreenFC.elements.setElementAt(element, elementIndex);
                                }
                            }

                            targetScreenFC.repaint();
                        }
                    }
                    else if (type == Writer.OTHER) {
                        url = API_URL + "/posts";

                        JSONObject jsonContent = new JSONObject();
                        jsonContent.put("content", text);
                        jsonContent.put("wallRecipientId", writer.getRecipientId());
                        content = jsonContent.toString();
                    }

                    try {
                        postRequest(url, content.getBytes("UTF-8"), refreshToken);
                    } catch (UnsupportedEncodingException ignored) {}

                    display.setCurrent(new Alert("Опубликовано", null, null, AlertType.CONFIRMATION), targetScreen);
                }
                else if (command == backToMenuCmd) {
                    display.setCurrent(targetScreen);
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

            outputStream.write(data != null ? data : new byte[] {});
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
        if (ITD.DEBUG) System.out.println(integer);
    }


    static void log(boolean bool) {
        if (ITD.DEBUG) System.out.println(bool);
    }


    private void initTokenForm() {
        this.tokenForm = new Form("Вход");

        try {
            if (refreshTokenRec.getNumRecords() == 1) {
                refreshToken = new String(refreshTokenRec.getRecord(1), "UTF-8");
            }
        } catch (Exception e) { throw new RuntimeException(e.toString()); }

        if (refreshToken != null) {
            if (testRefreshToken(refreshToken)) {
                startPrintln("Запуск фида...");
                initFeedCanvas();
                return;
            }

            tokenForm.append("Не удалось проверить токен. Возможно, он истёк, или сервера итд недоступны.\n");
        }

        tokenForm.append("Введите refresh-токен. Он находится в cookie браузера.");

        keyInput = new TextField("Токен:", null, 64, TextField.ANY);
        tokenForm.append(keyInput);

        StringItem enterButton = new StringItem(null, "Ввод", Item.BUTTON);
        enterButton.setDefaultCommand(keyEnterCommand);
        enterButton.setItemCommandListener(startCmdListener);
        tokenForm.append(enterButton);

        if (refreshToken != null) {
            StringItem retryButton = new StringItem(null, "Повторить", Item.BUTTON);
            retryButton.setDefaultCommand(keyRetryCommand);
            retryButton.setItemCommandListener(startCmdListener);
            tokenForm.append(retryButton);
        }

        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(tokenForm);
    }


    private void initFeedCanvas() {
        FeedCanvas feedCanvas = new FeedCanvas(this);

        loaderSleep();
        display.setCurrent(feedCanvas);
    }


    private void initMenuList() {
        menuList = new List("Меню", List.IMPLICIT, menuStrings, menuIcons);
        menuList.setCommandListener(menuCmdListener);
        menuList.addCommand(selectCmd);
        menuList.addCommand(aboutCmd);
    }


    private void initProfileCanvas() {
        final String profileUrl = API_URL + "/users/me";
        ProfileCanvas profileCanvas = new ProfileCanvas(this, profileUrl);

        loaderSleep();
        display.setCurrent(profileCanvas);
    }


    private void initSettingsForm() {
        Form settingsForm = new Form("Настройки");
        settingsForm.setCommandListener(settingsCmdListener);
        settingsForm.addCommand(backToMenuCmd);

        ChoiceGroup settingsGroup = new ChoiceGroup(null, Choice.MULTIPLE);
        settingsGroup.append("", null);
        settingsGroup.append("Прогружать посты по мере прокрутки", null);
        settingsForm.append(settingsGroup);

        loaderSleep(); //потому что ж2ме лоудер крашится без этого
        display.setCurrent(settingsForm);
    }


    private void initSettingsCmds() {
        settingsCmdListener = new CommandListener() {
            public void commandAction(Command command, Displayable displayable) {
                if (command == backToMenuCmd) {
                    loaderSleep(); //потому что ж2ме лоудер крашится без этого
                    display.setCurrent(menuList);
                }
            }
        };
    }


    private void initAboutForm() {
        Form aboutForm = new Form("О программе");

        aboutForm.setCommandListener(aboutCmdListener);
        aboutForm.addCommand(backToMenuCmd);

        StringItem title = new StringItem(null, "итд J2ME v" + appVersion + " (β)\n");
        title.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        title.setLayout(Item.LAYOUT_CENTER);
        aboutForm.append(title);

        aboutForm.append("by ultimate_fish\n");

        Image logoImg = null;
        logoImg = getPNGRes("itd");
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


    void initWriter(int type, String recipientId, String postId, String name, Displayable targetScreen) {
        if (targetScreen == null) {
            targetScreen = menuList;
        }
        Writer writer = new Writer(type, recipientId, postId, name, targetScreen);
        writer.setCommandListener(writerCmdListener);
        writer.addCommand(backToMenuCmd);
        writer.addCommand(postCmd);
        display.setCurrent(writer);
    }


    void initWriter() {
        initWriter(Writer.SELF, null, null, null, null);
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


    public static Image getPNGRes(String path) {
        try {
            return Image.createImage(Class.class.getResourceAsStream("/" + path + ".png"));
        } catch (IOException e) { throw new RuntimeException(e.toString()); }
    }


    static void loaderSleep() {
        try {
            Thread.sleep(J2ME_LOADER_FIX_SLEEP);
        } catch (InterruptedException ignored) {}
    }


    private void setIconSize() {
        int lineHeight = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL).getHeight();
        for (int i = 0; i < FONT_THRESHOLD.length; i++) {
            if (lineHeight > FONT_THRESHOLD[i][0]) {
                iconSize = FONT_THRESHOLD[i][1];
                avatarSize = FONT_THRESHOLD[i][2];
            }
            else {
                break;
            }
        }
    }


    public Image getIcon(String iconName) throws IOException {
        return Image.createImage(Class.class.getResourceAsStream("/" + iconSize + "px/" + iconName + ".png"));
    }


    void startPrintln(String text) {
        if (startForm.isShown()) startForm.append(text + "\n");
    }


    boolean testRefreshToken(String refreshToken) {
        for (int attempt = 0; attempt < 3; attempt++) {
            startPrintln("Проверка токена...");

            String isTokenValid = getRequest(URL + "/valid", refreshToken);
            if (isTokenValid != null && isTokenValid.equals("true")) {
                return true;
            }
        }

        return false;
    }
}
