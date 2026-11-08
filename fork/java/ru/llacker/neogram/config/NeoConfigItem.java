package ru.llacker.neogram.config;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One persisted Neogram option. Values are loaded lazily from {@link NeoConfig#preferences()} and
 * written with {@code apply()}, like the stock {@code org.telegram.utils.settings} settings, but
 * items also know whether they may be exported to a backup and notify listeners on change.
 */
public abstract class NeoConfigItem<T> implements NeoValue<T> {

    public interface Listener<T> {
        void onChanged(NeoConfigItem<T> item, T value);
    }

    public final String key;
    public final T defaultValue;
    private boolean exportable = true;

    private volatile boolean loaded;
    private volatile T value;
    private final List<Listener<T>> listeners = new CopyOnWriteArrayList<>();

    protected NeoConfigItem(@NonNull String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
        NeoConfig.register(this);
    }

    /** Not included in settings backups (device-specific or secret values). */
    @SuppressWarnings("unchecked")
    public <I extends NeoConfigItem<T>> I notExportable() {
        exportable = false;
        return (I) this;
    }

    public boolean isExportable() {
        return exportable;
    }

    @Override
    public T get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    SharedPreferences prefs = NeoConfig.preferences();
                    value = prefs.contains(key) ? read(prefs) : defaultValue;
                    loaded = true;
                }
            }
        }
        return value;
    }

    @Override
    public void set(T newValue) {
        synchronized (this) {
            value = newValue;
            loaded = true;
            SharedPreferences.Editor editor = NeoConfig.preferences().edit();
            write(editor, newValue);
            editor.apply();
        }
        for (Listener<T> listener : listeners) {
            listener.onChanged(this, newValue);
        }
    }

    public boolean isDefault() {
        T current = get();
        return current == null ? defaultValue == null : current.equals(defaultValue);
    }

    /** Back to the default value; the key is removed so a later default change applies. */
    public void reset() {
        synchronized (this) {
            value = defaultValue;
            loaded = true;
            NeoConfig.preferences().edit().remove(key).apply();
        }
        for (Listener<T> listener : listeners) {
            listener.onChanged(this, defaultValue);
        }
    }

    public void addListener(Listener<T> listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener<T> listener) {
        listeners.remove(listener);
    }

    public List<Listener<T>> listeners() {
        return Collections.unmodifiableList(listeners);
    }

    /** Called after a bulk import wrote preferences directly. */
    void invalidate() {
        loaded = false;
    }

    protected abstract T read(SharedPreferences prefs);

    protected abstract void write(SharedPreferences.Editor editor, T value);

    /** JSON-compatible representation for backups (Boolean, Number, String or JSONArray). */
    public abstract Object toJson(T value);

    /** Parses a backup value; returns null when the value has the wrong type (it is skipped). */
    public abstract T fromJson(Object json);

    // ---- concrete types -----------------------------------------------------------------------

    public static final class BoolItem extends NeoConfigItem<Boolean> {
        BoolItem(String key, boolean defaultValue) {
            super(key, defaultValue);
        }

        /** Unboxed value, the common case at call sites in stock code. */
        public boolean value() {
            return get();
        }

        public boolean toggle() {
            boolean next = !get();
            set(next);
            return next;
        }

        @Override
        protected Boolean read(SharedPreferences prefs) {
            return prefs.getBoolean(key, defaultValue);
        }

        @Override
        protected void write(SharedPreferences.Editor editor, Boolean value) {
            editor.putBoolean(key, value);
        }

        @Override
        public Object toJson(Boolean value) {
            return value;
        }

        @Override
        public Boolean fromJson(Object json) {
            return json instanceof Boolean ? (Boolean) json : null;
        }
    }

    public static final class IntItem extends NeoConfigItem<Integer> {
        IntItem(String key, int defaultValue) {
            super(key, defaultValue);
        }

        public int value() {
            return get();
        }

        @Override
        protected Integer read(SharedPreferences prefs) {
            return prefs.getInt(key, defaultValue);
        }

        @Override
        protected void write(SharedPreferences.Editor editor, Integer value) {
            editor.putInt(key, value);
        }

        @Override
        public Object toJson(Integer value) {
            return value;
        }

        @Override
        public Integer fromJson(Object json) {
            return json instanceof Number ? ((Number) json).intValue() : null;
        }
    }

    public static final class LongItem extends NeoConfigItem<java.lang.Long> {
        LongItem(String key, long defaultValue) {
            super(key, defaultValue);
        }

        public long value() {
            return get();
        }

        @Override
        protected java.lang.Long read(SharedPreferences prefs) {
            return prefs.getLong(key, defaultValue);
        }

        @Override
        protected void write(SharedPreferences.Editor editor, java.lang.Long value) {
            editor.putLong(key, value);
        }

        @Override
        public Object toJson(java.lang.Long value) {
            return value;
        }

        @Override
        public java.lang.Long fromJson(Object json) {
            return json instanceof Number ? ((Number) json).longValue() : null;
        }
    }

    public static final class FloatItem extends NeoConfigItem<java.lang.Float> {
        FloatItem(String key, float defaultValue) {
            super(key, defaultValue);
        }

        public float value() {
            return get();
        }

        @Override
        protected java.lang.Float read(SharedPreferences prefs) {
            return prefs.getFloat(key, defaultValue);
        }

        @Override
        protected void write(SharedPreferences.Editor editor, java.lang.Float value) {
            editor.putFloat(key, value);
        }

        @Override
        public Object toJson(java.lang.Float value) {
            return value.doubleValue();
        }

        @Override
        public java.lang.Float fromJson(Object json) {
            return json instanceof Number ? ((Number) json).floatValue() : null;
        }
    }

    public static final class StringItem extends NeoConfigItem<String> {
        StringItem(String key, String defaultValue) {
            super(key, defaultValue);
        }

        @Override
        protected String read(SharedPreferences prefs) {
            return prefs.getString(key, defaultValue);
        }

        @Override
        protected void write(SharedPreferences.Editor editor, String value) {
            editor.putString(key, value);
        }

        @Override
        public Object toJson(String value) {
            return value == null ? org.json.JSONObject.NULL : value;
        }

        @Override
        public String fromJson(Object json) {
            return json instanceof String ? (String) json : null;
        }
    }

    /** Stored by enum constant name, so reordering constants never changes a saved choice. */
    public static final class EnumItem<E extends Enum<E>> extends NeoConfigItem<E> {
        EnumItem(String key, @NonNull E defaultValue) {
            super(key, defaultValue);
        }

        private E parse(String name) {
            if (name != null) {
                try {
                    return Enum.valueOf(defaultValue.getDeclaringClass(), name);
                } catch (IllegalArgumentException ignore) {
                    // a removed constant: fall back to the default
                }
            }
            return null;
        }

        @Override
        protected E read(SharedPreferences prefs) {
            E value = parse(prefs.getString(key, null));
            return value != null ? value : defaultValue;
        }

        @Override
        protected void write(SharedPreferences.Editor editor, E value) {
            editor.putString(key, value.name());
        }

        @Override
        public Object toJson(E value) {
            return value.name();
        }

        @Override
        public E fromJson(Object json) {
            return json instanceof String ? parse((String) json) : null;
        }
    }

    public static final class StringSetItem extends NeoConfigItem<java.util.Set<String>> {
        StringSetItem(String key, java.util.Set<String> defaultValue) {
            super(key, java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(defaultValue)));
        }

        @Override
        protected java.util.Set<String> read(SharedPreferences prefs) {
            java.util.Set<String> stored = prefs.getStringSet(key, null);
            return stored == null ? defaultValue : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(stored));
        }

        @Override
        protected void write(SharedPreferences.Editor editor, java.util.Set<String> value) {
            editor.putStringSet(key, new java.util.HashSet<>(value));
        }

        @Override
        public Object toJson(java.util.Set<String> value) {
            return NeoConfig.toJsonArray(value);
        }

        @Override
        public java.util.Set<String> fromJson(Object json) {
            if (!(json instanceof org.json.JSONArray)) {
                return null;
            }
            org.json.JSONArray array = (org.json.JSONArray) json;
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                Object v = array.opt(i);
                if (!(v instanceof String)) {
                    return null;
                }
                result.add((String) v);
            }
            return java.util.Collections.unmodifiableSet(result);
        }
    }
}
