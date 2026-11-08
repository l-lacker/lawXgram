package ru.llacker.neogram.settings.sections;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.ItemOptions;

import java.util.List;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.backup.NeoBackup;
import ru.llacker.neogram.config.NeoConfig;
import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.settings.NeoSettingsPage;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;
import ru.llacker.neogram.update.NeoUpdateDialog;
import ru.llacker.neogram.update.NeoUpdater;

/** "About Neogram": links of the project and resetting Neogram options. */
public final class AboutSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("about", R.string.NeoSectionAbout,
            R.string.NeoSectionAboutInfo, R.drawable.neo_section_about, IconBackgroundColors.BLUE_LIGHT, AboutSection::rows);

    private AboutSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoUpdatesHeader).onlyIf(NeoUpdater::isEnabled));
        rows.add(NeoSettingsRow.action("check_updates", R.string.NeoCheckUpdate, R.string.NeoCheckUpdateInfo,
                (page, view) -> checkForUpdates(page)).onlyIf(NeoUpdater::isEnabled));
        rows.add(NeoSettingsRow.toggle("auto_update_check", NeoOptions.AUTO_UPDATE_CHECK,
                R.string.NeoAutoUpdateCheck, R.string.NeoAutoUpdateCheckInfo).onlyIf(NeoUpdater::isEnabled));

        rows.add(NeoSettingsRow.header(R.string.NeoBackupHeader));
        rows.add(NeoSettingsRow.action("export_settings", R.string.NeoBackupExport, 0, AboutSection::exportSettings));
        rows.add(NeoSettingsRow.action("import_settings", R.string.NeoBackupImport, R.string.NeoBackupInfo,
                (page, view) -> importSettings(page)));

        rows.add(NeoSettingsRow.header(R.string.NeoLinksHeader));
        rows.add(NeoSettingsRow.action("source_code", R.string.NeoViewSourceCode, R.string.NeoViewSourceCodeInfo,
                (page, view) -> open(page, NeoBrand.sourceCodeUrl())).onlyIf(() -> NeoBrand.hasLink(NeoBrand.sourceCodeUrl())));
        rows.add(NeoSettingsRow.action("report_problem", R.string.NeoReportProblem, R.string.NeoReportProblemInfo,
                (page, view) -> open(page, NeoBrand.issuesUrl())).onlyIf(() -> NeoBrand.hasLink(NeoBrand.issuesUrl())));
        rows.add(NeoSettingsRow.action("channel", R.string.NeoOfficialChannel, 0,
                (page, view) -> open(page, NeoBrand.channelUrl())).onlyIf(() -> NeoBrand.hasLink(NeoBrand.channelUrl())));
        rows.add(NeoSettingsRow.action("translate", R.string.NeoTranslation, 0,
                (page, view) -> open(page, NeoBrand.translateUrl())).onlyIf(() -> NeoBrand.hasLink(NeoBrand.translateUrl())));
        rows.add(NeoSettingsRow.action("donate", R.string.NeoDonate, 0,
                (page, view) -> open(page, NeoBrand.donateUrl())).onlyIf(() -> NeoBrand.hasLink(NeoBrand.donateUrl())));

        rows.add(NeoSettingsRow.header(R.string.NeoDangerZone));
        rows.add(NeoSettingsRow.action("reset", R.string.NeoResetSettings, R.string.NeoResetSettingsInfo,
                (page, view) -> confirmReset(page)).asDestructive());
    }

    private static void checkForUpdates(NeoSettingsPage page) {
        if (page.getParentActivity() == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(page.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(300);
        NeoUpdater.check(true, () -> {
            progress.dismiss();
            BetaUpdate update = NeoUpdater.getUpdate();
            if (update != null) {
                NeoUpdateDialog.show(page.getParentActivity(), update);
            } else if (NeoUpdater.lastCheckFailed()) {
                BulletinFactory.of(page).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.NeoUpdateCheckFailed)).show();
            } else {
                BulletinFactory.of(page).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.YourVersionIsLatest)).show();
            }
        });
    }

    private static void exportSettings(NeoSettingsPage page, @Nullable View anchor) {
        if (page.getParentActivity() == null) {
            return;
        }
        ItemOptions.makeOptions(page, anchor != null ? anchor : page.getFragmentView())
                .add(R.drawable.msg_download, NeoStrings.get(R.string.NeoBackupToFile), () -> saveToFile(page))
                .add(R.drawable.msg_saved, NeoStrings.get(R.string.NeoBackupToSaved), () -> sendToSaved(page))
                .show();
    }

    private static void saveToFile(NeoSettingsPage page) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(NeoBackup.MIME)
                .putExtra(Intent.EXTRA_TITLE, NeoBackup.fileName());
        page.startForResult(intent, (resultCode, data) -> {
            Uri uri = data != null && resultCode == Activity.RESULT_OK ? data.getData() : null;
            if (uri == null) {
                return;
            }
            Context context = ApplicationLoader.applicationContext;
            Utilities.globalQueue.postRunnable(() -> {
                boolean done = false;
                try {
                    NeoBackup.write(context, uri);
                    done = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                boolean saved = done;
                AndroidUtilities.runOnUIThread(() -> bulletin(page, saved, saved ? R.string.NeoBackupSaved : R.string.NeoBackupFailed));
            });
        });
    }

    private static void sendToSaved(NeoSettingsPage page) {
        int account = page.getCurrentAccount();
        Utilities.globalQueue.postRunnable(() -> {
            boolean done = false;
            try {
                NeoBackup.sendToSavedMessages(account);
                done = true;
            } catch (Exception e) {
                FileLog.e(e);
            }
            boolean sent = done;
            AndroidUtilities.runOnUIThread(() -> bulletin(page, sent, sent ? R.string.NeoBackupSent : R.string.NeoBackupFailed));
        });
    }

    private static void importSettings(NeoSettingsPage page) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{NeoBackup.MIME, "text/plain", "application/octet-stream"});
        page.startForResult(intent, (resultCode, data) -> {
            Uri uri = data != null && resultCode == Activity.RESULT_OK ? data.getData() : null;
            if (uri == null) {
                return;
            }
            Context context = ApplicationLoader.applicationContext;
            Utilities.globalQueue.postRunnable(() -> {
                JSONObject backup = null;
                try {
                    backup = NeoBackup.read(context, uri);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                JSONObject read = backup;
                AndroidUtilities.runOnUIThread(() -> {
                    if (read == null) {
                        bulletin(page, false, R.string.NeoBackupReadFailed);
                    } else {
                        confirmImport(page, read);
                    }
                });
            });
        });
    }

    private static void confirmImport(NeoSettingsPage page, JSONObject backup) {
        if (page.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(page.getParentActivity());
        builder.setTitle(NeoStrings.get(R.string.NeoBackupImport));
        builder.setMessage(NeoStrings.get(R.string.NeoBackupImportConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            NeoBackup.Result result = NeoBackup.restore(backup);
            if (result.invalid) {
                bulletin(page, false, R.string.NeoBackupInvalid);
                return;
            }
            page.refresh();
            String text = result.skipped > 0
                    ? LocaleController.formatString(R.string.NeoBackupImportedSkipped, result.applied, result.skipped)
                    : LocaleController.formatString(R.string.NeoBackupImported, result.applied);
            BulletinFactory.of(page).createSimpleBulletin(R.raw.contact_check, text).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        page.showDialog(builder.create());
    }

    private static void bulletin(NeoSettingsPage page, boolean success, int textRes) {
        BulletinFactory.of(page).createSimpleBulletin(success ? R.raw.contact_check : R.raw.error, NeoStrings.get(textRes)).show();
    }

    private static void open(NeoSettingsPage page, String url) {
        if (page.getParentActivity() != null) {
            Browser.openUrl(page.getParentActivity(), url);
        }
    }

    private static void confirmReset(NeoSettingsPage page) {
        if (page.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(page.getParentActivity());
        builder.setTitle(NeoStrings.get(R.string.NeoResetSettings));
        builder.setMessage(NeoStrings.get(R.string.NeoResetSettingsConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.Reset), (dialog, which) -> {
            NeoConfig.resetAll();
            page.refresh();
            BulletinFactory.of(page).createSimpleBulletin(R.raw.chats_infotip, NeoStrings.get(R.string.NeoResetSettingsDone)).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        page.showDialog(dialog);
        dialog.redPositive();
    }
}
