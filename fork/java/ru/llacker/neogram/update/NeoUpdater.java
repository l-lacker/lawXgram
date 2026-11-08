package ru.llacker.neogram.update;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.web.HttpGetFileTask;
import org.telegram.ui.web.HttpGetTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.core.update.ReleaseAssets;

/**
 * Updates of direct (non-Play) Neogram builds from the latest GitHub release of the repository
 * in fork/branding.properties. Plugged into Telegram's own custom-update hooks of
 * ApplicationLoader (NeoApplicationLoader), so LaunchActivity checks on start and MainTabsActivity
 * shows the update bar ({@link NeoUpdateLayout}).
 *
 * <p>The APK for the device ABI is picked from the release assets; after downloading it is checked
 * against the release SHA256SUMS (when present) and must be the same package signed with the same
 * certificate as the installed app, so an update never fails at the installer or replaces the app
 * with something else. State lives in its own preferences, not in exportable settings.</p>
 */
public final class NeoUpdater {

    private static final String PREFS = "neoupdates";
    private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final long MAXIMUM_APK_SIZE = 512L * 1024 * 1024;
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private static boolean loaded;
    private static String tag;
    private static String versionName;
    private static long versionCode;
    private static String apkName;
    private static String apkUrl;
    private static String checksumsUrl;
    private static String changelog;
    private static String path;
    private static long lastCheck;

    private static boolean checking;
    private static boolean lastCheckFailed;
    private static final List<Runnable> checkCallbacks = new ArrayList<>();
    private static boolean downloading;
    private static float progress;
    private static HttpGetFileTask downloadTask;

    private NeoUpdater() {
    }

    /** Release builds installed outside Google Play, when a repository is configured. */
    public static boolean isEnabled() {
        return NeoBrand.isDirectBuild()
                && !TextUtils.isEmpty(NeoBrand.updatesRepository())
                && NeoBrand.releaseApplicationId().equals(NeoBrand.packageName());
    }

    // ---- check --------------------------------------------------------------------------------

    /**
     * Asks GitHub for the latest release. Automatic checks run at most once a day and only when
     * enabled in settings; {@code force} always checks and shows a skipped version again.
     */
    @MainThread
    public static void check(boolean force, @Nullable Runnable whenDone) {
        load();
        if (whenDone != null) {
            checkCallbacks.add(whenDone);
        }
        if (checking) {
            return;
        }
        if (!isEnabled() || !force && (!NeoOptions.AUTO_UPDATE_CHECK.value()
                || Math.abs(System.currentTimeMillis() - lastCheck) < AUTO_CHECK_INTERVAL_MS)) {
            finishCheck();
            return;
        }
        if (force && !TextUtils.isEmpty(NeoOptions.SKIPPED_UPDATE.get())) {
            NeoOptions.SKIPPED_UPDATE.set("");
        }
        checking = true;
        new HttpGetTask(NeoUpdater::onRelease)
                .setHeader("Accept", "application/vnd.github+json")
                .setHeader("X-GitHub-Api-Version", "2022-11-28")
                .execute("https://api.github.com/repos/" + NeoBrand.updatesRepository() + "/releases/latest");
    }

    private static void onRelease(@Nullable String json) {
        checking = false;
        lastCheckFailed = true;
        if (json != null) {
            try {
                lastCheckFailed = !apply(new JSONObject(json));
            } catch (JSONException e) {
                FileLog.e(e);
            }
        }
        finishCheck();
    }

    /** Whether the last check that reached GitHub got no usable answer (offline, rate limit). */
    public static boolean lastCheckFailed() {
        return lastCheckFailed;
    }

    private static void finishCheck() {
        List<Runnable> callbacks = new ArrayList<>(checkCallbacks);
        checkCallbacks.clear();
        for (Runnable callback : callbacks) {
            callback.run();
        }
    }

    /** False for an answer without a release (rate limit, no releases yet): the known state stays. */
    private static boolean apply(@NonNull JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null || release.optBoolean("draft") || release.optBoolean("prerelease")) {
            FileLog.d("neo update: no usable release: " + release.optString("message"));
            return false;
        }
        List<String> names = new ArrayList<>();
        Map<String, String> urls = new HashMap<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null) {
                names.add(asset.optString("name"));
                urls.put(asset.optString("name"), asset.optString("browser_download_url"));
            }
        }
        ReleaseAssets.Apk apk = ReleaseAssets.pick(names, NeoBrand.apkBaseName(), Build.SUPPORTED_ABIS);
        long oldVersionCode = versionCode;
        lastCheck = System.currentTimeMillis();
        if (apk == null || apk.versionCode <= installedVersionCode() || TextUtils.isEmpty(urls.get(apk.name))) {
            clearUpdate();
        } else {
            if (apk.versionCode != versionCode || !apk.name.equals(apkName)) {
                deleteDownloaded();
            }
            tag = release.optString("tag_name");
            versionName = apk.versionName;
            versionCode = apk.versionCode;
            apkName = apk.name;
            apkUrl = urls.get(apk.name);
            checksumsUrl = urls.get(ReleaseAssets.CHECKSUMS);
            changelog = release.optString("body", null);
        }
        save();
        if (versionCode != oldVersionCode) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }
        return true;
    }

    /** The pending update, or null (none, or skipped by the user). */
    @Nullable
    public static BetaUpdate getUpdate() {
        load();
        if (versionName == null || versionCode <= 0 || versionCode <= installedVersionCode()) {
            return null;
        }
        if (tag != null && tag.equals(NeoOptions.SKIPPED_UPDATE.get())) {
            return null;
        }
        return new BetaUpdate(versionName, (int) versionCode, changelog);
    }

    /** "Skip this version": no popup or update bar until a newer release or a manual check. */
    public static void skip() {
        load();
        if (tag != null) {
            NeoOptions.SKIPPED_UPDATE.set(tag);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }
    }

    public static long lastCheckTime() {
        load();
        return lastCheck;
    }

    // ---- download -----------------------------------------------------------------------------

    @MainThread
    public static void download() {
        load();
        if (downloading || TextUtils.isEmpty(apkUrl) || TextUtils.isEmpty(apkName) || getDownloadedFile() != null) {
            return;
        }
        File directory = updatesDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            FileLog.e("neo update: cannot create " + directory);
            return;
        }
        downloading = true;
        progress = 0f;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        final String expectedName = apkName;
        downloadTask = new HttpGetFileTask(file -> AndroidUtilities.runOnUIThread(() -> onDownloaded(file, expectedName)), value -> {
            progress = value;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        }).setDestFile(new File(directory, expectedName)).setMaxSize(MAXIMUM_APK_SIZE);
        downloadTask.execute(apkUrl);
    }

    private static void onDownloaded(@Nullable File file, @NonNull String expectedName) {
        downloadTask = null;
        if (!downloading) {
            deleteQuietly(file); // cancelled meanwhile
            return;
        }
        if (file == null || !file.isFile() || !expectedName.equals(apkName)) {
            failDownload(file);
            return;
        }
        if (TextUtils.isEmpty(checksumsUrl)) {
            verifyAndKeep(file, null);
            return;
        }
        new HttpGetTask(text -> {
            String expected = text == null ? null : ReleaseAssets.parseChecksums(text).get(expectedName);
            if (expected == null) {
                FileLog.e("neo update: no checksum for " + expectedName);
                failDownload(file);
            } else {
                verifyAndKeep(file, expected);
            }
        }).execute(checksumsUrl);
    }

    /** Checksum, package and certificate are checked off the main thread. */
    private static void verifyAndKeep(@NonNull File file, @Nullable String expectedSha256) {
        Utilities.globalQueue.postRunnable(() -> {
            String problem = verify(file, expectedSha256);
            AndroidUtilities.runOnUIThread(() -> {
                if (problem != null) {
                    FileLog.e("neo update: rejected " + file.getName() + ": " + problem);
                    failDownload(file);
                    return;
                }
                path = file.getAbsolutePath();
                save();
                progress = 1f;
                downloading = false;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            });
        });
    }

    @Nullable
    private static String verify(@NonNull File file, @Nullable String expectedSha256) {
        if (expectedSha256 != null) {
            String actual = sha256(file);
            if (!expectedSha256.equals(actual)) {
                return "checksum " + actual + " != " + expectedSha256;
            }
        }
        PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), signatureFlags());
        if (archive == null) {
            return "not an APK";
        }
        if (!NeoBrand.packageName().equals(archive.packageName)) {
            return "package " + archive.packageName;
        }
        try {
            PackageInfo installed = pm.getPackageInfo(NeoBrand.packageName(), signatureFlags());
            if (!Arrays.equals(signatures(installed), signatures(archive))) {
                return "signed with another certificate";
            }
        } catch (PackageManager.NameNotFoundException e) {
            return "installed package not found";
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static int signatureFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Signature[] signatures(@NonNull PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    @Nullable
    private static String sha256(@NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void failDownload(@Nullable File file) {
        deleteQuietly(file);
        downloading = false;
        progress = 0f;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @MainThread
    public static void cancelDownload() {
        if (!downloading) {
            return;
        }
        if (downloadTask != null) {
            downloadTask.cancel(false);
            downloadTask = null;
        }
        downloading = false;
        progress = 0f;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    public static boolean isDownloading() {
        return downloading;
    }

    public static float getDownloadProgress() {
        return progress;
    }

    @Nullable
    public static File getDownloadedFile() {
        load();
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            path = null;
            save();
            return null;
        }
        return file;
    }

    /** Opens the system installer for the downloaded APK (asks for the install permission first). */
    public static boolean install(@Nullable Activity activity) {
        File file = getDownloadedFile();
        if (activity == null || file == null) {
            return false;
        }
        return AndroidUtilities.openForView(file, file.getName(), APK_MIME, activity, null, false);
    }

    // ---- state --------------------------------------------------------------------------------

    private static long installedVersionCode() {
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : versionCodeOf(info);
        } catch (Exception e) {
            FileLog.e(e);
            return Long.MAX_VALUE; // unknown: never offer an "update"
        }
    }

    @SuppressWarnings("deprecation")
    private static long versionCodeOf(PackageInfo info) {
        return info.versionCode;
    }

    @NonNull
    private static File updatesDirectory() {
        // files/cache is shared through the stock FileProvider (xml/provider_paths.xml)
        return new File(new File(ApplicationLoader.getFilesDirFixed(), "cache"), "updates");
    }

    private static void clearUpdate() {
        deleteDownloaded();
        tag = null;
        versionName = null;
        versionCode = 0;
        apkName = null;
        apkUrl = null;
        checksumsUrl = null;
        changelog = null;
    }

    private static void deleteDownloaded() {
        if (path != null) {
            deleteQuietly(new File(path));
            path = null;
        }
    }

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            FileLog.e("neo update: cannot delete " + file);
        }
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        SharedPreferences prefs = preferences();
        tag = prefs.getString("tag", null);
        versionName = prefs.getString("version_name", null);
        versionCode = prefs.getLong("version_code", 0L);
        apkName = prefs.getString("apk_name", null);
        apkUrl = prefs.getString("apk_url", null);
        checksumsUrl = prefs.getString("checksums_url", null);
        changelog = prefs.getString("changelog", null);
        path = prefs.getString("path", null);
        lastCheck = prefs.getLong("last_check", 0L);
        if (versionCode != 0 && versionCode <= installedVersionCode()) {
            clearUpdate(); // the update was installed
            save();
        }
    }

    private static void save() {
        preferences().edit()
                .putString("tag", tag)
                .putString("version_name", versionName)
                .putLong("version_code", versionCode)
                .putString("apk_name", apkName)
                .putString("apk_url", apkUrl)
                .putString("checksums_url", checksumsUrl)
                .putString("changelog", changelog)
                .putString("path", path)
                .putLong("last_check", lastCheck)
                .apply();
    }
}
