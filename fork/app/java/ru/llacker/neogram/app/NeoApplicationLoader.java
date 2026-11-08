package ru.llacker.neogram.app;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import org.telegram.messenger.ApplicationLoaderImpl;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.regular.BuildConfig;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

import ru.llacker.neogram.NeoHooks;
import ru.llacker.neogram.dialogs.NeoDialogs;
import ru.llacker.neogram.update.NeoUpdateDialog;
import ru.llacker.neogram.update.NeoUpdateLayout;
import ru.llacker.neogram.update.NeoUpdater;

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

    // ---- updates: Telegram's custom-update hooks, served from GitHub releases (NeoUpdater) ----

    /** Never fall back to Telegram's own help.getAppUpdate: it would offer the official APK. */
    @Override
    public boolean isCustomUpdate() {
        return true;
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        NeoUpdater.check(force, whenDone);
    }

    @Override
    public BetaUpdate getUpdate() {
        return NeoUpdater.getUpdate();
    }

    @Override
    public void downloadUpdate() {
        NeoUpdater.download();
    }

    @Override
    public void cancelDownloadingUpdate() {
        NeoUpdater.cancelDownload();
    }

    @Override
    public boolean isDownloadingUpdate() {
        return NeoUpdater.isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return NeoUpdater.getDownloadProgress();
    }

    @Override
    public File getDownloadedUpdateFile() {
        return NeoUpdater.getDownloadedFile();
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        return NeoUpdateDialog.show(context, update);
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return NeoUpdater.isEnabled() ? new NeoUpdateLayout(activity, sideMenuContainer) : null;
    }

    /** Items of the chat list menu (three dots), after New Group and Saved Messages. */
    @Override
    public void addItemOptions(ItemOptions itemOptions) {
        super.addItemOptions(itemOptions);
        NeoDialogs.addMenuItems(itemOptions);
    }
}
