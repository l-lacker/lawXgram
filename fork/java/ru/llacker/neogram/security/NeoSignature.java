package ru.llacker.neogram.security;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

/**
 * Refuses to run a direct release build that was re-signed with another key, i.e. repackaged
 * (patch misc/signature-check, native part in fork/jni/colorado).
 *
 * <p>Safe by construction: the expected certificate is taken from the release keystore at build
 * time (nothing hard-coded, forks building with their own key check against their own key), the
 * native check is only compiled into release builds that have a keystore, it only runs in direct
 * builds (Google Play re-signs Play builds with its own key) and it only acts on a certificate
 * that was actually read and differs; an unreadable or missing APK is not treated as tampering.</p>
 */
public final class NeoSignature {

    private static boolean scheduled;

    private NeoSignature() {
    }

    /**
     * The native library loads with the first activity (ApplicationLoader.postInitApplication), so
     * the check runs right after the first activity was created.
     */
    public static void schedule(@NonNull Application application) {
        if (scheduled || !BuildConfig.NEO_SIGNATURE_CHECK || !ApplicationLoader.isStandaloneBuild()) {
            return;
        }
        scheduled = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                application.unregisterActivityLifecycleCallbacks(this);
                AndroidUtilities.runOnUIThread(NeoSignature::verify);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    private static void verify() {
        int result;
        try {
            result = nativeVerify();
        } catch (UnsatisfiedLinkError e) {
            FileLog.e(e); // the native library is not loaded (yet): nothing is known
            return;
        }
        if (result == 0) {
            FileLog.e("neo: this APK is signed with another certificate than the release key");
            Process.killProcess(Process.myPid());
            System.exit(10);
        }
    }

    /** 1: signed with the release key, 0: signed with another key, -1: unknown. */
    private static native int nativeVerify();
}
