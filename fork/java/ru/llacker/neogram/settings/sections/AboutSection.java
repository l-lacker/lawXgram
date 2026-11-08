package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.config.NeoConfig;
import ru.llacker.neogram.settings.NeoSettingsPage;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** "About Neogram": links of the project and resetting Neogram options. */
public final class AboutSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("about", R.string.NeoSectionAbout,
            R.string.NeoSectionAboutInfo, R.drawable.neo_section_about, IconBackgroundColors.BLUE_LIGHT, AboutSection::rows);

    private AboutSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
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
