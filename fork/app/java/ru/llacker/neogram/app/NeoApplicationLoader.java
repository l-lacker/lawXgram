package ru.llacker.neogram.app;

import org.telegram.messenger.ApplicationLoaderImpl;
import org.telegram.messenger.regular.BuildConfig;

import ru.llacker.neogram.NeoHooks;

/**
 * Neogram's Application class (set by fork/app/AndroidManifest.xml). Uses the stock
 * ApplicationLoader extension points instead of patching ApplicationLoader itself.
 */
public class NeoApplicationLoader extends ApplicationLoaderImpl {

    private static final String BUILD_TYPE_PLAY = "play";

    @Override
    public void onCreate() {
        NeoHooks.beforeApplicationCreate(this);
        super.onCreate();
        NeoHooks.afterApplicationCreate(this);
    }

    /**
     * Direct (non-Google Play) builds behave like Telegram's own direct-download build:
     * in-app updates, invoice billing, platform restrictions of the store builds do not apply.
     */
    @Override
    protected boolean isStandalone() {
        return !BUILD_TYPE_PLAY.equals(BuildConfig.BUILD_TYPE);
    }

    /** Never fall back to Telegram's own help.getAppUpdate: it would offer the official APK. */
    @Override
    public boolean isCustomUpdate() {
        return true;
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        if (whenDone != null) {
            whenDone.run();
        }
    }
}
