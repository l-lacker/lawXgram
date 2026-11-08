package ru.llacker.neogram;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import ru.llacker.neogram.config.NeoOptions;

/** Notification options (patches feature/silence-non-contacts and feature/notification-color). */
public final class NeoNotifications {

    /** Telegram's service account: login codes and security notices always keep their sound. */
    private static final long SERVICE_USER_ID = 777000L;

    /** Colour of Telegram's message notifications. */
    private static final int STOCK_COLOR = 0xff11acfa;

    private NeoNotifications() {
    }

    /**
     * Colour of message notifications: the stock blue, or the accent of the current theme. An
     * accent too light or too dark to read on a notification falls back to the theme's header blue.
     */
    public static int color() {
        if (!NeoOptions.ACCENT_NOTIFICATION_COLOR.value()) {
            return STOCK_COLOR;
        }
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        int color = theme != null && theme.hasAccentColors() ? theme.getAccentColor(theme.currentAccentId) : 0;
        if (color == 0) {
            color = Theme.getColor(Theme.key_actionBarDefault) | 0xff000000;
        }
        float brightness = AndroidUtilities.computePerceivedBrightness(color);
        if (brightness >= 0.721f || brightness <= 0.279f) {
            color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xff000000;
        }
        return color;
    }

    /**
     * Whether a private chat notification goes out without sound because the sender is not a
     * contact. Groups and channels keep their own settings.
     */
    public static boolean isSilenced(int account, @Nullable TLRPC.Chat chat, long dialogId) {
        if (!NeoOptions.SILENCE_NON_CONTACTS.value() || chat != null) {
            return false;
        }
        long userId = dialogId;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(account)
                    .getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedChat == null) {
                return false;
            }
            userId = encryptedChat.user_id;
        }
        if (userId <= 0 || userId == SERVICE_USER_ID || userId == UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        return ContactsController.getInstance(account).contactsDict.get(userId) == null;
    }
}
