package ru.llacker.lawxgram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PreferencesMigrationHelper {
    private static final String MIGRATION_MARKER_PREFIX = "__prefs_migrated__";

    private PreferencesMigrationHelper() {
    }

    public static String getMigrationMarkerKey(String preferencesName) {
        return MIGRATION_MARKER_PREFIX + preferencesName;
    }

    public static boolean isMigrationMarkerKey(String key) {
        return key != null && key.startsWith(MIGRATION_MARKER_PREFIX);
    }

    public static SharedPreferences getSharedPreferences(Context context, String preferencesName, String legacyPreferencesName) {
        SharedPreferences preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        String migrationMarkerKey = getMigrationMarkerKey(preferencesName);
        if (preferences.contains(migrationMarkerKey)) {
            return preferences;
        }
        if (!preferences.getAll().isEmpty()) {
            preferences.edit().putBoolean(migrationMarkerKey, true).apply();
            return preferences;
        }
        if (legacyPreferencesName == null) {
            return preferences;
        }

        SharedPreferences legacyPreferences = context.getSharedPreferences(legacyPreferencesName, Context.MODE_PRIVATE);
        Map<String, ?> legacyValues = legacyPreferences.getAll();
        if (legacyValues.isEmpty()) {
            preferences.edit().putBoolean(migrationMarkerKey, true).apply();
            return preferences;
        }

        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : legacyValues.entrySet()) {
            putPreferenceValue(editor, entry.getKey(), entry.getValue());
        }
        editor.putBoolean(migrationMarkerKey, true);
        editor.apply();
        return preferences;
    }

    public static void putPreferenceValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set<?>) {
            HashSet<String> stringSet = new HashSet<>();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) {
                    stringSet.add((String) item);
                }
            }
            editor.putStringSet(key, stringSet);
        }
    }
}
