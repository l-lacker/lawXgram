package ru.llacker.neogram;

import ru.llacker.neogram.config.NeoOptions;

/** Interface options read while drawing (patch debloat/app-bar-shadow); values are cached in memory. */
public final class NeoAppearance {

    private NeoAppearance() {
    }

    public static boolean hideAppBarShadow() {
        return NeoOptions.HIDE_APP_BAR_SHADOW.value();
    }
}
