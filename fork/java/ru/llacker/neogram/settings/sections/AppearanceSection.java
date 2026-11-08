package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "appearance". Rows are added here as features are ported; empty sections are hidden. */
public final class AppearanceSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("appearance", R.string.NeoSectionAppearance,
            R.string.NeoSectionAppearanceInfo, R.drawable.neo_section_appearance, IconBackgroundColors.PURPLE, AppearanceSection::rows);

    private AppearanceSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
