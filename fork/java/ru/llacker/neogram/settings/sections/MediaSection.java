package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "media": video messages and, as features are ported, playback and downloads. */
public final class MediaSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("media", R.string.NeoSectionMedia,
            R.string.NeoSectionMediaInfo, R.drawable.neo_section_media, IconBackgroundColors.RED, MediaSection::rows);

    private MediaSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.page("round_video", RoundVideoSection.SECTION, R.string.NeoRoundVideoInfo));
    }
}
