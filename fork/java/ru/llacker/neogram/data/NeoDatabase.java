package ru.llacker.neogram.data;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;

/**
 * Neogram's own tables inside each account's cache database. The stock schema is never touched:
 * Neogram tables are prefixed {@code neo_} and versioned in {@code neo_meta}, so the stock
 * migrations (LAST_DB_VERSION) and Neogram migrations are independent.
 */
public final class NeoDatabase {

    /** Bump together with a new step in {@link #migrate}. */
    static final int SCHEMA_VERSION = 1;
    private static final String KEY_SCHEMA_VERSION = "schema_version";

    private NeoDatabase() {
    }

    /**
     * Called by MessagesStorage.openDatabase on the storage queue after the stock migrations
     * (patch misc/database). Never throws: a failure here must not send the stock code into its
     * "malformed database" cleanup path.
     */
    public static void onOpened(MessagesStorage storage, SQLiteDatabase database) {
        try {
            database.executeFast("CREATE TABLE IF NOT EXISTS neo_meta(key TEXT PRIMARY KEY, value TEXT)").stepThis().dispose();
            migrate(database, readInt(database, KEY_SCHEMA_VERSION, 0));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void migrate(SQLiteDatabase database, int version) throws Exception {
        if (version >= SCHEMA_VERSION) {
            return;
        }
        database.beginTransaction();
        try {
            if (version < 1) {
                // folder emoticons: the server keeps them, this is the offline cache
                database.executeFast("CREATE TABLE IF NOT EXISTS neo_dialog_filter_ext(id INTEGER PRIMARY KEY, emoticon TEXT)").stepThis().dispose();
                version = 1;
            }
            writeInt(database, KEY_SCHEMA_VERSION, version);
            database.commitTransaction();
        } catch (Exception e) {
            database.commitTransaction();
            throw e;
        }
    }

    static int readInt(SQLiteDatabase database, String key, int fallback) throws Exception {
        SQLiteCursor cursor = database.queryFinalized("SELECT value FROM neo_meta WHERE key = ?", key);
        try {
            if (cursor.next()) {
                return Integer.parseInt(cursor.stringValue(0));
            }
        } catch (NumberFormatException ignore) {
            // treated as absent
        } finally {
            cursor.dispose();
        }
        return fallback;
    }

    static void writeInt(SQLiteDatabase database, String key, int value) throws Exception {
        database.executeFast("REPLACE INTO neo_meta VALUES('" + key + "', '" + value + "')").stepThis().dispose();
    }
}
