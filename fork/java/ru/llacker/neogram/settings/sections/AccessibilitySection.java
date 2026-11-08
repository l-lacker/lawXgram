package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "accessibility". Rows are added here as features are ported; empty sections are hidden. */
public final class AccessibilitySection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("accessibility", R.string.NeoSectionAccessibility,
            R.string.NeoSectionAccessibilityInfo, R.drawable.neo_section_accessibility, IconBackgroundColors.BLUE_DEEP, AccessibilitySection::rows);

    private AccessibilitySection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
