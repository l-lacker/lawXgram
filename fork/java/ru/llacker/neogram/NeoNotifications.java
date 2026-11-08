package ru.llacker.neogram;

import androidx.annotation.Nullable;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import ru.llacker.neogram.config.NeoOptions;

/** Notification options (patch feature/silence-non-contacts, hook in NotificationsController). */
public final class NeoNotifications {

    /** Telegram's service account: login codes and security notices always keep their sound. */
    private static final long SERVICE_USER_ID = 777000L;

    private NeoNotifications() {
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
