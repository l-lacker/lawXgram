package ru.llacker.neogram;

import android.app.Application;

/**
 * Entry points called from stock code and from NeoApplicationLoader. This class only dispatches:
 * feature logic lives in the helpers of the corresponding feature.
 */
public final class NeoHooks {

    private NeoHooks() {
    }

    /** Before ApplicationLoader.onCreate(): nothing of Telegram is initialized yet. */
    public static void beforeApplicationCreate(Application application) {
    }

    /** After ApplicationLoader.onCreate(): applicationContext and the main handler exist. */
    public static void afterApplicationCreate(Application application) {
        ru.llacker.neogram.security.NeoSignature.schedule(application);
    }
}
