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

/** Settings section "chats": the message menu, double tap and the chat screen. */
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

        rows.add(NeoSettingsRow.header(R.string.NeoChatViewHeader));
        rows.add(NeoSettingsRow.toggle("hide_time_on_stickers", NeoOptions.HIDE_TIME_ON_STICKERS,
                R.string.NeoHideTimeOnSticker, R.string.NeoHideTimeOnStickerInfo));
        rows.add(NeoSettingsRow.toggle("disable_next_channel", NeoOptions.DISABLE_NEXT_CHANNEL,
                R.string.NeoDisableJumpToNextChannel, R.string.NeoDisableJumpToNextChannelInfo));
        rows.add(NeoSettingsRow.toggle("hide_business_bot_panel", NeoOptions.HIDE_BUSINESS_BOT_PANEL,
                R.string.NeoHideBusinessBotPanel, R.string.NeoHideBusinessBotPanelAbout));
        rows.add(NeoSettingsRow.toggle("hide_keyboard_on_scroll", NeoOptions.HIDE_KEYBOARD_ON_SCROLL,
                R.string.NeoHideKeyboardOnChatScroll, R.string.NeoHideKeyboardOnChatScrollInfo));
        rows.add(NeoSettingsRow.toggle("delete_for_all_default", NeoOptions.DELETE_FOR_ALL_DEFAULT,
                R.string.NeoDeleteForAllByDefault, R.string.NeoDeleteForAllByDefaultInfo));
        rows.add(NeoSettingsRow.toggle("links_in_instant_view", NeoOptions.LINKS_IN_INSTANT_VIEW,
                R.string.NeoOpenAllLinksInInstantView, R.string.NeoOpenAllLinksInInstantViewInfo));
        rows.add(NeoSettingsRow.toggle("hide_greeting_sticker", NeoOptions.HIDE_GREETING_STICKER,
                R.string.NeoDisableGreetingSticker, R.string.NeoDisableGreetingStickerInfo));
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
