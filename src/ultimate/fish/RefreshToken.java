package ultimate.fish;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public class RefreshToken {
    private final String REC_NAME;

    private String refreshToken = null;
    private RecordStore rec;


    RefreshToken(String recName) {
        this.REC_NAME = recName;
        ITD.log("Открытие хранилища записей...");
        try {
            this.rec = RecordStore.openRecordStore(recName, true);

            if (rec.getNumRecords() > 0) {
                this.refreshToken = new String(rec.getRecord(1));
            }
        } catch (RecordStoreException e) { throw new RuntimeException(e.toString()); }
    }


    String get() {
        return this.refreshToken;
    }


    boolean isEmpty() {
        return this.refreshToken == null;
    }


    void set(final String refreshToken) {
        if (refreshToken == null) {
            return;
        }

        this.refreshToken = refreshToken;

        ITD.log("Запись токена в RMS...");
        new Thread(new Runnable() {
            public void run() {
                byte[] refreshTokenBytes = refreshToken.getBytes();

                try {
                    rec.closeRecordStore();
                    RecordStore.deleteRecordStore(REC_NAME);
                    rec = RecordStore.openRecordStore(REC_NAME, true);
                    rec.addRecord(refreshTokenBytes, 0, refreshTokenBytes.length);

                    setRec(rec); // ваще посрать на всю потоковую безопасность, оно раз в пять лет выполняется
                } catch (RecordStoreException e) { throw new RuntimeException(e.toString()); }
            }
        }, "tokenWriter").start();
    }


    private void setRec(RecordStore rec) {
        this.rec = rec;
    }


    void close() {
        try {
            rec.closeRecordStore();
        } catch (RecordStoreException e) {
            ITD.log("Хранилище токена не закрылось: " + e.toString());
        }
    }
}
