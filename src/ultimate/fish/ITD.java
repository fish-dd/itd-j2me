package ultimate.fish;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class ITD extends MIDlet {
    static final boolean DEBUG = true;
    private boolean isAlreadyRunning = false;

    private Display display;

    static String[] URLS = {"http://127.0.0.1:5000", "http://192.168.31.99:5000", "http://ultimatefish.ddns.net:5000"};
    static String URL = URLS[0];
    static String API_URL = URL + "/api";
    static String NAME = "итд";

    private Command keyEnterCommand = new Command("Ввод", Command.OK, 1);
    private Form tokenForm;

    private FeedCanvas feedForm;

    private Thread connectThread;
    private String refreshToken;

    private final String RECORD_STORE_NAME = "itd-db";
    private final int REFRESH_TOKEN_RECORD_ID = 1;
    private RecordStore recorder;


    protected void startApp() {
        if (isAlreadyRunning) return;
        isAlreadyRunning = true;

        display = Display.getDisplay(this);

        try {
            recorder = RecordStore.openRecordStore(RECORD_STORE_NAME, true, RecordStore.AUTHMODE_PRIVATE, true);
        } catch (RecordStoreException e) { throw new RuntimeException(e.toString()); }

        try {
            if (recorder.getNumRecords() >= 1) {
                this.refreshToken = new String(recorder.getRecord(REFRESH_TOKEN_RECORD_ID), "UTF-8");
                initFeedForm();
            }
            else {
                initTokenForm();
            }
        } catch (Exception e) { throw new RuntimeException(e.toString()); }
    }


    protected void pauseApp() {}


    protected void destroyApp(boolean unconditional) {}


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
            if (code == 200) { // Код может быть не только 200, а 204 например
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


    private void initTokenForm() {
        this.tokenForm = new Form("Вход");

        tokenForm.append("Введите refresh-токен");

        final TextField keyInput = new TextField("Токен:", null, 128, TextField.ANY);
        tokenForm.append(keyInput);

        StringItem button = new StringItem(null, "Ввод", Item.BUTTON);
        button.setDefaultCommand(keyEnterCommand);
        ItemCommandListener commandListener = new ItemCommandListener() {
            public void commandAction(Command command, Item item) {
                tokenForm.deleteAll();

                try {
                    refreshToken = keyInput.getString();
                    byte[] refreshTokenBytes = refreshToken.getBytes();
                    recorder.setRecord(REFRESH_TOKEN_RECORD_ID, refreshTokenBytes, 0, refreshTokenBytes.length);
                } catch (Exception e) { throw new RuntimeException(e.toString()); }

                initFeedForm();
            }
        };
        button.setItemCommandListener(commandListener);
        tokenForm.append(button);

        display.setCurrent(tokenForm);
    }


    private void initFeedForm() {
        final String url = API_URL + "/posts?limit=20&tab=popular";
        final ITD midlet = this;

        Runnable getPostsRunnable = new Runnable() {
            public void run() {
                String postsResponse = getRequest(url,  refreshToken);
                JSONObject json = JSON.getObject(postsResponse);
                JSONArray posts = json.getObject("data").getArray("posts");
//                for (int i = 0; i < posts.size(); i++) {
//                    JSONObject post = (JSONObject) posts.get(i);
//                    feedForm.append(post.getString("content") + "\n----------\n");
//                }
                feedForm = new FeedCanvas(posts, midlet);
                display.setCurrent(feedForm);
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
}
