package ru.llacker.neogram.config;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry and storage of Neogram options (SharedPreferences file {@value #PREFS}).
 * Options themselves are declared in {@link NeoOptions}.
 */
public final class NeoConfig {

    public static final String PREFS = "neoconfig";
    /** Version of the backup format written by {@link #exportJson()}. */
    public static final int BACKUP_VERSION = 1;

    private static final Map<String, NeoConfigItem<?>> ITEMS = new LinkedHashMap<>();
    private static volatile SharedPreferences preferences;

    private NeoConfig() {
    }

    static synchronized void register(NeoConfigItem<?> item) {
        if (ITEMS.containsKey(item.key)) {
            throw new IllegalStateException("duplicate Neogram option key " + item.key);
        }
        ITEMS.put(item.key, item);
    }

    public static SharedPreferences preferences() {
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            synchronized (NeoConfig.class) {
                prefs = preferences;
                if (prefs == null) {
                    prefs = preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
                }
            }
        }
        return prefs;
    }

    /** All registered options, in declaration order. */
    public static List<NeoConfigItem<?>> items() {
        // class initialization of NeoOptions registers items: never trigger it under our monitor
        NeoOptions.ensureLoaded();
        synchronized (NeoConfig.class) {
            return Collections.unmodifiableList(new ArrayList<>(ITEMS.values()));
        }
    }

    public static NeoConfigItem<?> find(String key) {
        NeoOptions.ensureLoaded();
        synchronized (NeoConfig.class) {
            return ITEMS.get(key);
        }
    }

    // ---- item factories ---------------------------------------------------------------------

    public static NeoConfigItem.BoolItem bool(String key, boolean defaultValue) {
        return new NeoConfigItem.BoolItem(key, defaultValue);
    }

    public static NeoConfigItem.IntItem integer(String key, int defaultValue) {
        return new NeoConfigItem.IntItem(key, defaultValue);
    }

    public static NeoConfigItem.LongItem longValue(String key, long defaultValue) {
        return new NeoConfigItem.LongItem(key, defaultValue);
    }

    public static NeoConfigItem.FloatItem floatValue(String key, float defaultValue) {
        return new NeoConfigItem.FloatItem(key, defaultValue);
    }

    public static NeoConfigItem.StringItem string(String key, String defaultValue) {
        return new NeoConfigItem.StringItem(key, defaultValue);
    }

    public static <E extends Enum<E>> NeoConfigItem.EnumItem<E> choice(String key, @NonNull E defaultValue) {
        return new NeoConfigItem.EnumItem<>(key, defaultValue);
    }

    public static NeoConfigItem.StringSetItem stringSet(String key, Set<String> defaultValue) {
        return new NeoConfigItem.StringSetItem(key, defaultValue);
    }

    // ---- backup -----------------------------------------------------------------------------

    /** Exportable options that differ from their defaults. */
    public static JSONObject exportJson() {
        JSONObject root = new JSONObject();
        JSONObject values = new JSONObject();
        try {
            root.put("format", "neogram-settings");
            root.put("version", BACKUP_VERSION);
            for (NeoConfigItem<?> item : items()) {
                if (item.isExportable() && !item.isDefault()) {
                    values.put(item.key, exportValue(item));
                }
            }
            root.put("values", values);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    private static <T> Object exportValue(NeoConfigItem<T> item) {
        return item.toJson(item.get());
    }

    /**
     * Applies a backup written by {@link #exportJson()}. Unknown keys and values of the wrong type
     * are skipped and reported in the result.
     */
    public static ImportResult importJson(JSONObject root) {
        ImportResult result = new ImportResult();
        JSONObject values = root.optJSONObject("values");
        if (values == null || !"neogram-settings".equals(root.optString("format"))) {
            result.invalidFormat = true;
            return result;
        }
        for (java.util.Iterator<String> it = values.keys(); it.hasNext(); ) {
            String key = it.next();
            NeoConfigItem<?> item = find(key);
            if (item == null || !item.isExportable()) {
                result.skipped.add(key);
                continue;
            }
            if (importValue(item, values.opt(key))) {
                result.applied++;
            } else {
                result.skipped.add(key);
            }
        }
        return result;
    }

    private static <T> boolean importValue(NeoConfigItem<T> item, Object json) {
        T value = item.fromJson(json);
        if (value == null) {
            return false;
        }
        item.set(value);
        return true;
    }

    /** Resets every option to its default. */
    public static void resetAll() {
        for (NeoConfigItem<?> item : items()) {
            item.reset();
        }
    }

    public static final class ImportResult {
        public boolean invalidFormat;
        public int applied;
        public final Set<String> skipped = new HashSet<>();
    }

    static JSONArray toJsonArray(Set<String> set) {
        JSONArray array = new JSONArray();
        for (String s : set) {
            array.put(s);
        }
        return array;
    }
}
