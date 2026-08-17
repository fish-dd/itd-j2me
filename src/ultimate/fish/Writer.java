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
    private final String postId;
    private final Displayable targetScreen;

    public Writer(int type, String recipientId, String postId, String name, Displayable targetScreen) {
        super(null, null, limit, settings);

        this.type = type;
        this.recipientId = recipientId;
        this.postId = postId;
        this.targetScreen = targetScreen;

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
            setTitle("Ответ " + name);
        }
    }

    public int getType() {
        return type;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getPostId() {
        return postId;
    }

    public Displayable getTargetScreen() {
        return targetScreen;
    }
}
