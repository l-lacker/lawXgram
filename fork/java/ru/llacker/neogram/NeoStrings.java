package ru.llacker.neogram;

import androidx.annotation.StringRes;

import org.telegram.messenger.LocaleController;

/**
 * Neogram strings never contain the app name: strings that mention it take it as the format
 * argument %1$s (see tools/legacy/strings-keymap.tsv). This helper fills that argument without
 * running String.format, so strings with other % signs stay intact.
 */
public final class NeoStrings {

    private NeoStrings() {
    }

    public static String get(@StringRes int res) {
        return withAppName(LocaleController.getString(res));
    }

    public static String withAppName(String text) {
        return text == null ? null : text.replace("%1$s", NeoBrand.appName());
    }
}
