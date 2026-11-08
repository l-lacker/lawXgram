package ru.llacker.neogram;

import android.app.Activity;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import ru.llacker.neogram.config.NeoOptions;

/** "Ask before calling" (patch feature/ask-before-call, hook in VoIPHelper.startCall). */
public final class NeoCalls {

    /**
     * After the user confirmed a call, the same person is not asked about again for a minute: the
     * stock code calls startCall once more when the microphone or camera permission was granted.
     */
    private static final long APPROVAL_MS = 60_000L;

    private static long approvedUserId;
    private static long approvedUntil;

    private NeoCalls() {
    }

    /**
     * True when a confirmation is shown instead of calling; {@code call} repeats the call after
     * the user confirmed.
     */
    public static boolean askFirst(@Nullable TLRPC.User user, boolean video, @Nullable Activity activity, @NonNull Runnable call) {
        if (!NeoOptions.ASK_BEFORE_CALL.value() || user == null || activity == null || activity.isFinishing()) {
            return false;
        }
        if (user.id == approvedUserId && SystemClock.elapsedRealtime() < approvedUntil) {
            return false;
        }
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(video ? R.string.VideoCallAlertTitle : R.string.CallAlertTitle))
                .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(video ? R.string.VideoCallAlert : R.string.CallAlert,
                        UserObject.getUserName(user))))
                .setPositiveButton(LocaleController.getString(R.string.Call), (dialog, which) -> {
                    approvedUserId = user.id;
                    approvedUntil = SystemClock.elapsedRealtime() + APPROVAL_MS;
                    call.run();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
        return true;
    }
}
