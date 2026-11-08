package ru.llacker.neogram;

import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;

/**
 * Neogram identity. All values come from fork/branding.properties through BuildConfig, so the
 * app can be renamed without touching code or patches.
 */
public final class NeoBrand {

    private NeoBrand() {
    }

    public static String appName() {
        return BuildConfig.NEO_APP_NAME;
    }

    public static String appNameBeta() {
        return BuildConfig.NEO_APP_NAME_BETA;
    }

    /** The installed package (application id with the build type suffix). */
    public static String packageName() {
        return ApplicationLoader.applicationContext.getPackageName();
    }

    /** System account type of the contacts sync adapter; equals the generated xml/auth.xml. */
    public static String accountType() {
        return packageName();
    }

    /** Contacts data mime type, kind is "profile", "call" or "call.video". */
    public static String contactsMimeType(String kind) {
        return "vnd.android.cursor.item/vnd." + packageName() + ".android." + kind;
    }

    /** Folder name in shared storage (Pictures/, Download/, ...). */
    public static String mediaFolder() {
        return BuildConfig.NEO_MEDIA_FOLDER;
    }

    /** Application id of release builds (debug builds add a suffix). */
    public static String releaseApplicationId() {
        return BuildConfig.NEO_APPLICATION_ID;
    }

    /** First part of APK file names: {@code <apkBaseName>-<version>-<versionCode>-<abi>.apk}. */
    public static String apkBaseName() {
        return BuildConfig.NEO_APK_BASE_NAME;
    }

    public static boolean isDirectBuild() {
        return ApplicationLoader.isStandaloneBuild();
    }

    public static String sourceCodeUrl() {
        return BuildConfig.NEO_LINK_SOURCE_CODE;
    }

    public static String issuesUrl() {
        return BuildConfig.NEO_LINK_ISSUES;
    }

    public static String channelUrl() {
        return BuildConfig.NEO_LINK_CHANNEL;
    }

    public static String translateUrl() {
        return BuildConfig.NEO_LINK_TRANSLATE;
    }

    public static String donateUrl() {
        return BuildConfig.NEO_LINK_DONATE;
    }

    /** "owner/repo" whose GitHub releases deliver updates of direct builds. */
    public static String updatesRepository() {
        return BuildConfig.NEO_UPDATES_REPO;
    }

    public static String upstreamVersion() {
        return BuildConfig.NEO_UPSTREAM_VERSION;
    }

    public static boolean hasLink(String url) {
        return !TextUtils.isEmpty(url);
    }

    /**
     * Strings that must not come from Telegram's server language packs: they name the app.
     * Called first thing by LocaleController.getStringV2 (patch misc/branding); null = not ours.
     */
    public static String overrideString(String key, int stringRes) {
        if (stringRes == R.string.AppName || "AppName".equals(key)) {
            return appName();
        }
        if (stringRes == R.string.AppNameBeta || "AppNameBeta".equals(key)) {
            return appNameBeta();
        }
        return null;
    }

    /** "Neogram v13.2.1 (7291)" + separator + build kind and ABI, as shown in settings and logs. */
    public static String versionLine(String versionName, int versionCode, String abi, String separator) {
        return appName() + " v" + versionName + " (" + versionCode + ")" + separator + abi;
    }
}
