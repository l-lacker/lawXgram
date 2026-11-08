package ru.llacker.neogram.chat;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    // ---- reminder -------------------------------------------------------------------------------

    /** "Set a reminder": a scheduled copy in Saved Messages, which Telegram shows as a reminder. */
    static boolean canRemind(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        return isForwardable(message) && !chat.isInScheduleMode();
    }

    static void remind(@NonNull ChatActivity chat, @NonNull MessageObject message, @Nullable MessageObject.GroupedMessages group) {
        Context context = chat.getParentActivity();
        if (context == null) {
            return;
        }
        int account = chat.getCurrentAccount();
        long self = UserConfig.getInstance(account).getClientUserId();
        ArrayList<MessageObject> messages = messagesOf(message, group);
        AlertsCreator.createScheduleDatePickerDialog(context, self, (notify, scheduleDate, scheduleRepeatPeriod) -> {
            SendMessagesHelper.getInstance(account).sendMessage(messages, self, false, false, true, scheduleDate, 0);
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.saved_messages, NeoStrings.get(R.string.NeoReminderSet)).show();
        }, chat.getResourceProvider()).show();
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

    /** "Open in…" for a downloaded video the chat allows to save. */
    static boolean canOpenIn(@NonNull MessageObject message) {
        return message.isVideo() && message.canForwardMessage() && downloadedFile(message) != null;
    }

    static void openIn(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        if (chat.getParentActivity() != null) {
            AndroidUtilities.openForView(message, chat.getParentActivity(), chat.getResourceProvider(), false);
        }
    }

    // ---- stickers -------------------------------------------------------------------------------

    /** A downloaded static sticker (WebP); animated and video stickers are not images. */
    @Nullable
    static File stickerFile(@NonNull MessageObject message) {
        if (!message.isSticker() || message.isAnimatedSticker() || message.isVideoSticker() || !message.canForwardMessage()) {
            return null;
        }
        File file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        return file != null && file.isFile() ? file : null;
    }

    /** Saves the sticker to the gallery as a PNG with transparency. */
    static void saveSticker(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        Activity activity = chat.getParentActivity();
        File source = stickerFile(message);
        if (activity == null || source == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE)
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        // MediaController.saveFile refuses files in the app's internal storage: the PNG goes next to
        // Telegram's own media cache, where the sticker itself lives
        File png = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "sticker_" + message.getDialogId() + "_" + message.getId() + ".png");
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap image = BitmapFactory.decodeFile(source.getAbsolutePath());
            if (image == null) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(png)) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException e) {
                FileLog.e(e);
                return;
            } finally {
                image.recycle();
            }
            MediaController.saveFile(png.getAbsolutePath(), context, 0, null, "image/png", uri -> {
                if (!chat.isFinished && chat.getParentActivity() != null) {
                    BulletinFactory.of(chat).createDownloadBulletin(BulletinFactory.FileType.PHOTO, chat.getResourceProvider()).show();
                }
            });
        });
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

    /** Reads a QR code from the photo off the main thread and offers to open or copy it. */
    static void scanQr(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        File file = photoFile(message);
        if (file == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String text = null;
            try {
                text = NeoQr.decode(file);
            } catch (Exception | OutOfMemoryError e) {
                FileLog.e(e);
            }
            String result = text;
            AndroidUtilities.runOnUIThread(() -> showQr(chat, result));
        });
    }

    private static void showQr(@NonNull ChatActivity chat, @Nullable String text) {
        Context context = chat.getParentActivity();
        if (context == null) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, NeoStrings.get(R.string.NeoQrNotFound)).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, chat.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.QrCode));
        builder.setMessage(text);
        String scheme = Uri.parse(text).getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "tg".equalsIgnoreCase(scheme)) {
            builder.setPositiveButton(LocaleController.getString(R.string.Open), (dialog, which) -> Browser.openUrl(context, text));
        }
        builder.setNeutralButton(LocaleController.getString(R.string.Copy), (dialog, which) -> {
            AndroidUtilities.addToClipboard(text);
            if (AndroidUtilities.shouldShowClipboardToast()) {
                BulletinFactory.of(chat).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        chat.showDialog(builder.create());
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
