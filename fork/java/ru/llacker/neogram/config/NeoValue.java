package ru.llacker.neogram.config;

import androidx.annotation.NonNull;

/**
 * A readable and writable option value. Neogram options ({@link NeoConfigItem}) are values; so are
 * stock settings that Neogram pages edit in place ({@code SharedSettings}, policy helpers), wrapped
 * with {@link #of}.
 */
public interface NeoValue<T> {

    T get();

    void set(T value);

    interface Getter<T> {
        T get();
    }

    interface Setter<T> {
        void set(T value);
    }

    @NonNull
    static <T> NeoValue<T> of(@NonNull Getter<T> getter, @NonNull Setter<T> setter) {
        return new NeoValue<T>() {
            @Override
            public T get() {
                return getter.get();
            }

            @Override
            public void set(T value) {
                setter.set(value);
            }
        };
    }
}
