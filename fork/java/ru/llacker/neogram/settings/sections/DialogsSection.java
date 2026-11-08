package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "dialogs". Rows are added here as features are ported; empty sections are hidden. */
public final class DialogsSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("dialogs", R.string.NeoSectionDialogs,
            R.string.NeoSectionDialogsInfo, R.drawable.settings_folders, IconBackgroundColors.BLUE_ALT, DialogsSection::rows);

    private DialogsSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
