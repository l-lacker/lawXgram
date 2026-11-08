package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.chat.NeoDoubleTap;
import ru.llacker.neogram.config.NeoConfigItem;
import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "chats": the message menu, double tap and, as features are ported, chat tweaks. */
public final class ChatsSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("chats", R.string.NeoSectionChats,
            R.string.NeoSectionChatsInfo, R.drawable.settings_chat, IconBackgroundColors.ORANGE, ChatsSection::rows);

    private ChatsSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoMessagesHeader));
        rows.add(NeoSettingsRow.page("message_menu", MessageMenuSection.SECTION, R.string.NeoMessageMenuInfo));
        rows.add(doubleTap("double_tap_in", NeoOptions.DOUBLE_TAP_IN, R.string.NeoDoubleTapIncoming));
        rows.add(doubleTap("double_tap_out", NeoOptions.DOUBLE_TAP_OUT, R.string.NeoDoubleTapOutgoing));
    }

    private static NeoSettingsRow doubleTap(@NonNull String slug, @NonNull NeoConfigItem<NeoDoubleTap.Action> item, int titleRes) {
        return NeoSettingsRow.choice(slug, item, titleRes, R.string.NeoDoubleTapInfo)
                .option(NeoDoubleTap.Action.REACTION, R.string.NeoDoubleTapReaction)
                .option(NeoDoubleTap.Action.REPLY, R.string.NeoDoubleTapReply)
                .option(NeoDoubleTap.Action.SAVE, R.string.NeoAddToSavedMessages)
                .option(NeoDoubleTap.Action.REPEAT, R.string.NeoRepeat)
                .option(NeoDoubleTap.Action.EDIT, R.string.NeoDoubleTapEdit)
                .option(NeoDoubleTap.Action.NONE, R.string.NeoDoubleTapNone)
                .build();
    }
}
