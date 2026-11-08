package ru.llacker.neogram.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.BulletinFactory;

import ru.llacker.neogram.config.NeoOptions;

/** The ID row in user and chat profiles (patch feature/profile-id). */
public final class NeoProfile {

    /** How the ID is written: as the Telegram API has it, as the Bot API has it, or not shown. */
    public enum IdFormat { API, BOT_API, HIDDEN }

    private NeoProfile() {
    }

    public static boolean showId() {
        return NeoOptions.PROFILE_ID.get() != IdFormat.HIDDEN;
    }

    /**
     * Users are the same in both APIs. The Bot API writes basic groups as -id and channels and
     * supergroups as -100id; the Telegram API uses the bare chat id.
     */
    @NonNull
    static String formatId(long peerId, int account) {
        if (peerId >= 0) {
            return String.valueOf(peerId);
        }
        long chatId = -peerId;
        if (NeoOptions.PROFILE_ID.get() != IdFormat.BOT_API) {
            return String.valueOf(chatId);
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
        return ChatObject.isChannel(chat) ? "-100" + chatId : "-" + chatId;
    }

    /** Data centre of the peer: where its profile photo is stored, 0 when it has none. */
    static int dataCenter(long peerId, int account) {
        MessagesController controller = MessagesController.getInstance(account);
        if (peerId > 0) {
            TLRPC.User user = controller.getUser(peerId);
            return user != null && user.photo != null ? user.photo.dc_id : 0;
        }
        TLRPC.Chat chat = controller.getChat(-peerId);
        return chat != null && chat.photo != null ? chat.photo.dc_id : 0;
    }

    public static void bindId(@NonNull TextDetailCell cell, long peerId, int account) {
        int dc = dataCenter(peerId, account);
        String label = dc > 0 ? "ID · DC " + dc : "ID";
        cell.setTextAndValue(formatId(peerId, account), label, false);
        cell.setContentDescriptionValueFirst(true);
    }

    public static void copyId(@Nullable BaseFragment fragment, long peerId) {
        if (fragment == null) {
            return;
        }
        AndroidUtilities.addToClipboard(formatId(peerId, fragment.getCurrentAccount()));
        if (AndroidUtilities.shouldShowClipboardToast()) {
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.NeoIdCopied)).show();
        }
    }
}
