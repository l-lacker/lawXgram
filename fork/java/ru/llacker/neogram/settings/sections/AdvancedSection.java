package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "advanced". Rows are added here as features are ported; empty sections are hidden. */
public final class AdvancedSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("advanced", R.string.NeoSectionAdvanced,
            R.string.NeoSectionAdvancedInfo, R.drawable.neo_section_advanced, IconBackgroundColors.GRAY, AdvancedSection::rows);

    private AdvancedSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
