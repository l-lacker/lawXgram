package ru.llacker.neogram.chat;

import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Chat screen options: patches debloat/next-channel, debloat/business-bot-panel and
 * debloat/sticker-time.
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

    /** The time pill of this message is not drawn (stickers and large emoji, when the option is on). */
    public static boolean hideTime(@Nullable MessageObject message) {
        return message != null && message.isAnyKindOfSticker() && NeoOptions.HIDE_TIME_ON_STICKERS.value();
    }
}
