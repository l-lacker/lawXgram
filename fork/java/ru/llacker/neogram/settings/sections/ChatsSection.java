package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "chats". Rows are added here as features are ported; empty sections are hidden. */
public final class ChatsSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("chats", R.string.NeoSectionChats,
            R.string.NeoSectionChatsInfo, R.drawable.settings_chat, IconBackgroundColors.ORANGE, ChatsSection::rows);

    private ChatsSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
    }
}
