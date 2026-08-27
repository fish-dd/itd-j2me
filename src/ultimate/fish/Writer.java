package ultimate.fish;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

public class Writer extends TextBox {
    public static final int SELF = 0;
    public static final int REPOST = 1;
    public static final int OTHER = 2;
    public static final int COMMENT = 3;
    public static final int REPLY = 4;

    static final int limit = 1000;
    static final int settings = TextField.ANY;

    private final int type;
    private final String recipientId;
    private final String elementId;
    private final String name;
    private final Displayable targetScreen;
    private final int replyIndex;

    public Writer(int type, String recipientId, String elementId, String name, Displayable targetScreen, int replyIndex) {
        super(null, null, limit, settings);

        this.type = type;
        this.recipientId = recipientId;
        this.elementId = elementId;
        this.name = name;
        this.targetScreen = targetScreen;
        this.replyIndex = replyIndex;

        if (type == SELF) {
            setTitle("Написать пост");
        }
        else if (type == REPOST) {
            setTitle("Репост " + name);
        }
        else if (type == OTHER) {
            setTitle("Пост для " + name);
        }
        else if (type == COMMENT) {
            setTitle("Комментарий");
        }
        else if (type == REPLY) {
            setTitle("Ответ юзеру " + name);
        }
    }

    public int getType() {
        return type;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getElementId() {
        return elementId;
    }

    public String getName() {
        return name;
    }

    public Displayable getTargetScreen() {
        return targetScreen;
    }

    public int getReplyIndex() {
        return replyIndex;
    }
}
