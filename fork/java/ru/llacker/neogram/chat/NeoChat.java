package ru.llacker.neogram.chat;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatGreetingsView;

import ru.llacker.neogram.NeoStrings;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Chat screen options: patches debloat/next-channel, debloat/business-bot-panel,
 * debloat/sticker-time, debloat/greeting-sticker, feature/hide-keyboard-on-scroll,
 * feature/delete-for-all-default, feature/links-in-instant-view and feature/sticker-size.
 */
public final class NeoChat {

    private NeoChat() {
    }

    public static boolean disableNextChannel() {
        return NeoOptions.DISABLE_NEXT_CHANNEL.value();
    }

    public static boolean hideBusinessBotPanel() {
        return NeoOptions.HIDE_BUSINESS_BOT_PANEL.value();
    }

    /** Dragging the message list closes the keyboard, or the emoji and sticker panel when no keyboard is shown. */
    public static void onChatDragged(@NonNull ChatActivity chat) {
        if (!NeoOptions.HIDE_KEYBOARD_ON_SCROLL.value()) {
            return;
        }
        if (chat.isKeyboardVisible()) {
            Activity activity = chat.getParentActivity();
            if (activity != null) {
                AndroidUtilities.hideKeyboard(activity.getCurrentFocus());
            }
        } else if (chat.getChatActivityEnterView() != null) {
            chat.getChatActivityEnterView().hidePopup(true);
        }
    }

    /**
     * Empty chats without the greeting sticker: the sticker container is hidden and the text no
     * longer asks to tap it. A business intro, set later by ChatActivity, keeps its own text.
     */
    public static void onGreetingsCreated(@NonNull ChatGreetingsView view) {
        if (!NeoOptions.HIDE_GREETING_STICKER.value()) {
            return;
        }
        view.stickerContainer.setVisibility(android.view.View.GONE);
        view.setText(LocaleController.getString(R.string.NoMessages), NeoStrings.get(R.string.NeoGreetingNoSticker));
    }

    public static boolean deleteForAllByDefault() {
        return NeoOptions.DELETE_FOR_ALL_DEFAULT.value();
    }

    public static boolean openLinksInInstantView() {
        return NeoOptions.LINKS_IN_INSTANT_VIEW.value();
    }

    /** Scale of the box stickers are fitted into; animated emoji and dice keep their server zoom. */
    public static float stickerScale(@NonNull MessageObject message) {
        if (message.isAnimatedEmoji() || message.isDice()) {
            return 1f;
        }
        return NeoOptions.STICKER_SIZE.value() / 100f;
    }

    /** The time pill of this message is not drawn (stickers and large emoji, when the option is on). */
    public static boolean hideTime(@Nullable MessageObject message) {
        return message != null && message.isAnyKindOfSticker() && NeoOptions.HIDE_TIME_ON_STICKERS.value();
    }
}
