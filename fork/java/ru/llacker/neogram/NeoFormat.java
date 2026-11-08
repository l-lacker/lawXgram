package ru.llacker.neogram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.llacker.neogram.config.NeoOptions;

/** Time, number and name formatting options (patch feature/format-tweaks, hooks in LocaleController). */
public final class NeoFormat {

    private NeoFormat() {
    }

    /** LocaleController.getFormatterDay returns the stock formatter with seconds. */
    public static boolean timeWithSeconds() {
        return NeoOptions.TIME_WITH_SECONDS.value();
    }

    /** LocaleController.formatShortNumber does not round to K/M. */
    public static boolean exactNumbers() {
        return NeoOptions.EXACT_NUMBERS.value();
    }

    /**
     * LocaleController.nameDisplayOrder (1 = first name first, 2 = last name first): the user's
     * choice, or the stock order of the app language.
     */
    public static int nameOrder(int languageDefault) {
        switch (NeoOptions.NAME_ORDER.get()) {
            case FIRST_LAST:
                return 1;
            case LAST_FIRST:
                return 2;
            default:
                return languageDefault;
        }
    }

    /** formatShortNumber without rounding: the number itself, also as the "rounded" value. */
    @NonNull
    public static String exactNumber(int number, @Nullable int[] rounded) {
        if (rounded != null) {
            rounded[0] = number;
        }
        return String.valueOf(number);
    }
}
