package ru.llacker.neogram.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;

import ru.llacker.neogram.NeoStrings;

/** What the Neogram message menu items and double tap actions do. */
public final class NeoMessageActions {

    private NeoMessageActions() {
    }

    /** The message, or its whole album, in message order. */
    @NonNull
    static ArrayList<MessageObject> messagesOf(@NonNull MessageObject message, @Nullable MessageObject.GroupedMessages group) {
        ArrayList<MessageObject> messages = new ArrayList<>();
        if (group != null && !group.messages.isEmpty()) {
            messages.addAll(group.messages);
        } else {
            messages.add(message);
        }
        return messages;
    }

    /** A sent, ordinary message that may be forwarded. */
    static boolean isForwardable(@NonNull MessageObject message) {
        return message.getId() > 0
                && !message.isSending()
                && !message.scheduled
                && !(message.messageOwner instanceof TLRPC.TL_messageService)
                && !DialogObject.isEncryptedDialog(message.getDialogId())
                && message.canForwardMessage();
    }

    // ---- repeat ---------------------------------------------------------------------------------

    /** "+1": forward the message into the same chat. */
    static boolean canRepeat(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        if (!isForwardable(message) || chat.isInScheduleMode()) {
            return false;
        }
        TLRPC.Chat current = chat.getCurrentChat();
        return current == null || ChatObject.canSendMessages(current);
    }

    static void repeat(@NonNull ChatActivity chat, @NonNull MessageObject message, @Nullable MessageObject.GroupedMessages group) {
        chat.forwardMessages(messagesOf(message, group), false, false, true, 0, 0);
    }

    // ---- Saved Messages -------------------------------------------------------------------------

    static boolean canSave(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        return isForwardable(message) && chat.getDialogId() != UserConfig.getInstance(chat.getCurrentAccount()).getClientUserId();
    }

    static void saveToSavedMessages(@NonNull ChatActivity chat, @NonNull MessageObject message,
                                    @Nullable MessageObject.GroupedMessages group) {
        int account = chat.getCurrentAccount();
        long self = UserConfig.getInstance(account).getClientUserId();
        ArrayList<MessageObject> messages = messagesOf(message, group);
        SendMessagesHelper.getInstance(account).sendMessage(messages, self, false, false, true, 0, 0, null, -1, 0, 0, null);
        BulletinFactory.of(chat).createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceTags(LocaleController.getString(
                messages.size() > 1 ? R.string.FwdMessagesToSavedMessages : R.string.FwdMessageToSavedMessages))).show();
    }

    // ---- downloaded files -----------------------------------------------------------------------

    /** The file Telegram downloaded for the message (not a file the user sent from their device). */
    @Nullable
    static File downloadedFile(@NonNull MessageObject message) {
        if (message.getDocument() == null && !message.isPhoto() || message.isOut() && message.messageOwner.attachPath != null
                && !message.messageOwner.attachPath.isEmpty() && new File(message.messageOwner.attachPath).exists()) {
            return null;
        }
        File file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        return file != null && file.isFile() ? file : null;
    }

    static void deleteDownloadedFile(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        File file = downloadedFile(message);
        if (file == null) {
            return;
        }
        if (!file.delete()) {
            FileLog.e("neo: cannot delete " + file);
            return;
        }
        RecyclerListView list = chat.getChatListView();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getMessageObject() == message) {
                ((ChatMessageCell) child).updateButtonState(false, true, false);
            }
        }
    }

    // ---- photos ---------------------------------------------------------------------------------

    @Nullable
    static File photoFile(@NonNull MessageObject message) {
        if (!message.isPhoto()) {
            return null;
        }
        File file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        return file != null && file.isFile() ? file : null;
    }

    static void copyPhoto(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        File file = photoFile(message);
        if (file == null) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        try {
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(), "photo", uri));
            if (AndroidUtilities.shouldShowClipboardToast()) {
                BulletinFactory.of(chat).createSimpleBulletin(R.raw.copy, NeoStrings.get(R.string.NeoPhotoCopied)).show();
            }
        } catch (Exception e) {
            // a file outside the shared provider paths (internal cache on devices without external storage)
            FileLog.e(e);
        }
    }
}
