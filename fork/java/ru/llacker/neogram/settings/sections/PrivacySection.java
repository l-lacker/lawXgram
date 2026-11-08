package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "privacy". Rows are added here as features are ported; empty sections are hidden. */
public final class PrivacySection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("privacy", R.string.NeoSectionPrivacy,
            R.string.NeoSectionPrivacyInfo, R.drawable.settings_privacy, IconBackgroundColors.GREEN, PrivacySection::rows);

    private PrivacySection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
