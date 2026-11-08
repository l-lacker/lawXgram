package ru.llacker.neogram.update;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import ru.llacker.neogram.NeoBrand;

/** "Neogram X is available" with the release notes: download (or install), later, skip. */
public final class NeoUpdateDialog {

    private static final int MAXIMUM_NOTES_LENGTH = 1500;

    private NeoUpdateDialog() {
    }

    /** ApplicationLoader.showCustomUpdateAppPopup. */
    public static boolean show(@Nullable Context context, @Nullable BetaUpdate update) {
        if (context == null || update == null || context instanceof Activity && ((Activity) context).isFinishing()) {
            return false;
        }
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.formatString(R.string.NeoUpdateAvailable, NeoBrand.appName(), update.version));
            builder.setMessage(notes(update.changelog));
            boolean downloaded = NeoUpdater.getDownloadedFile() != null;
            builder.setPositiveButton(LocaleController.getString(downloaded ? R.string.AppUpdateNow : R.string.AppUpdateDownloadNow),
                    (dialog, which) -> {
                        if (NeoUpdater.getDownloadedFile() != null) {
                            NeoUpdater.install(AndroidUtilities.findActivity(context));
                        } else {
                            NeoUpdater.download();
                        }
                    });
            builder.setNegativeButton(LocaleController.getString(R.string.AppUpdateRemindMeLater), null);
            builder.setNeutralButton(LocaleController.getString(R.string.NeoUpdateSkip), (dialog, which) -> NeoUpdater.skip());
            builder.show();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Release notes as plain text: Markdown headings and emphasis markers removed, length capped. */
    @NonNull
    static CharSequence notes(@Nullable String changelog) {
        if (TextUtils.isEmpty(changelog)) {
            return "";
        }
        String text = changelog.replace("\r\n", "\n")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "• ")
                .replace("**", "")
                .trim();
        if (text.length() > MAXIMUM_NOTES_LENGTH) {
            text = text.substring(0, MAXIMUM_NOTES_LENGTH).trim() + "…";
        }
        return text;
    }
}
