package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "translator". Rows are added here as features are ported; empty sections are hidden. */
public final class TranslatorSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("translator", R.string.NeoSectionTranslator,
            R.string.NeoSectionTranslatorInfo, R.drawable.neo_section_translator, IconBackgroundColors.CYAN, TranslatorSection::rows);

    private TranslatorSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
