package ru.llacker.neogram.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.config.NeoOptions;

/**
 * Neogram items of the chat message menu (patch hooks/chat-message-menu): fill() runs at the end
 * of ChatActivity.fillMessageMenu, onSelected() for ids the stock switch does not know.
 */
public final class NeoMessageMenu {

    /** Neogram option ids start far above the stock OPTION_* values (0..117). */
    public static final int OPTION_REPEAT = 1001;
    public static final int OPTION_SAVE = 1002;
    public static final int OPTION_DELETE_FILE = 1003;
    public static final int OPTION_COPY_PHOTO = 1004;
    public static final int OPTION_DETAILS = 1005;
    public static final int OPTION_REMINDER = 1006;
    public static final int OPTION_OPEN_IN = 1007;
    public static final int OPTION_QR = 1008;

    private NeoMessageMenu() {
    }

    public static void fill(@NonNull ChatActivity chat, @Nullable MessageObject message, @Nullable MessageObject.GroupedMessages group,
                            @NonNull ArrayList<Integer> icons, @NonNull ArrayList<CharSequence> items, @NonNull ArrayList<Integer> options) {
        if (message == null || message.isSponsored() || chat.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            return;
        }
        try {
            if (!NeoOptions.MENU_REPORT.value()) {
                remove(options, icons, items, ChatActivity.OPTION_REPORT_CHAT);
            }
            int index = anchor(options);
            if (NeoOptions.MENU_REPEAT.value() && NeoMessageActions.canRepeat(chat, message)) {
                insert(index++, OPTION_REPEAT, R.drawable.msg_retry, NeoStrings.get(R.string.NeoRepeat), options, icons, items);
            }
            if (NeoOptions.MENU_SAVE.value() && NeoMessageActions.canSave(chat, message)) {
                insert(index++, OPTION_SAVE, R.drawable.msg_saved, NeoStrings.get(R.string.NeoAddToSavedMessages), options, icons, items);
            }
            if (NeoOptions.MENU_REMINDER.value() && NeoMessageActions.canRemind(chat, message)) {
                insert(index++, OPTION_REMINDER, R.drawable.msg_calendar2, LocaleController.getString(R.string.SetReminder),
                        options, icons, items);
            }
            if (NeoOptions.MENU_COPY_PHOTO.value() && group == null && NeoMessageActions.photoFile(message) != null) {
                insert(index++, OPTION_COPY_PHOTO, R.drawable.msg_copy, NeoStrings.get(R.string.NeoCopyPhoto), options, icons, items);
            }
            if (NeoOptions.MENU_QR.value() && group == null && NeoMessageActions.photoFile(message) != null) {
                insert(index++, OPTION_QR, R.drawable.msg_qrcode, LocaleController.getString(R.string.QrCode), options, icons, items);
            }
            if (NeoOptions.MENU_OPEN_IN.value() && group == null && NeoMessageActions.canOpenIn(message)) {
                insert(index++, OPTION_OPEN_IN, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp),
                        options, icons, items);
            }
            if (NeoOptions.MENU_STATISTICS.value() && canShowStatistics(chat, message) && !options.contains(ChatActivity.OPTION_STATISTICS)) {
                insert(index++, ChatActivity.OPTION_STATISTICS, R.drawable.msg_stats, LocaleController.getString(R.string.Statistics),
                        options, icons, items);
            }
            int end = options.indexOf(ChatActivity.OPTION_DELETE);
            if (end < 0) {
                end = options.size();
            }
            if (NeoOptions.MENU_DELETE_FILE.value() && group == null && NeoMessageActions.downloadedFile(message) != null) {
                insert(end++, OPTION_DELETE_FILE, R.drawable.msg_clear, NeoStrings.get(R.string.NeoDeleteDownloadedFile),
                        options, icons, items);
            }
            if (NeoOptions.MENU_DETAILS.value() && !message.scheduled) {
                insert(end, OPTION_DETAILS, R.drawable.msg_info, NeoStrings.get(R.string.NeoMessageDetails), options, icons, items);
            }
        } catch (Throwable e) {
            FileLog.e(e); // never break the stock menu
        }
    }

    /** Neogram items go after Forward, Reply or Copy, the actions they belong to. */
    private static int anchor(@NonNull ArrayList<Integer> options) {
        for (int option : new int[]{ChatActivity.OPTION_FORWARD, ChatActivity.OPTION_REPLY, ChatActivity.OPTION_COPY}) {
            int index = options.indexOf(option);
            if (index >= 0) {
                return index + 1;
            }
        }
        int delete = options.indexOf(ChatActivity.OPTION_DELETE);
        return delete >= 0 ? delete : options.size();
    }

    private static void insert(int index, int option, int icon, @NonNull CharSequence text, @NonNull ArrayList<Integer> options,
                               @NonNull ArrayList<Integer> icons, @NonNull ArrayList<CharSequence> items) {
        index = Math.max(0, Math.min(index, options.size()));
        options.add(index, option);
        icons.add(index, icon);
        items.add(index, text);
    }

    private static void remove(@NonNull ArrayList<Integer> options, @NonNull ArrayList<Integer> icons,
                               @NonNull ArrayList<CharSequence> items, int option) {
        for (int i = options.size() - 1; i >= 0; i--) {
            if (options.get(i) == option) {
                options.remove(i);
                icons.remove(i);
                items.remove(i);
            }
        }
    }

    /** Statistics of a channel post (stock MessageStatisticActivity), for its admins. */
    private static boolean canShowStatistics(@NonNull ChatActivity chat, @NonNull MessageObject message) {
        TLRPC.Chat current = chat.getCurrentChat();
        return current != null && ChatObject.isChannelAndNotMegaGroup(current) && ChatObject.hasAdminRights(current)
                && message.getId() > 0 && message.messageOwner.views > 0 && !message.isForwarded() && !message.scheduled;
    }

    /** True when the option is a Neogram one and was handled. */
    public static boolean onSelected(@NonNull ChatActivity chat, int option, @Nullable MessageObject message,
                                     @Nullable MessageObject.GroupedMessages group) {
        if (message == null) {
            return false;
        }
        switch (option) {
            case OPTION_REPEAT:
                NeoMessageActions.repeat(chat, message, group);
                return true;
            case OPTION_SAVE:
                NeoMessageActions.saveToSavedMessages(chat, message, group);
                return true;
            case OPTION_DELETE_FILE:
                NeoMessageActions.deleteDownloadedFile(chat, message);
                return true;
            case OPTION_COPY_PHOTO:
                NeoMessageActions.copyPhoto(chat, message);
                return true;
            case OPTION_DETAILS:
                chat.presentFragment(new NeoMessageDetails(message));
                return true;
            case OPTION_REMINDER:
                NeoMessageActions.remind(chat, message, group);
                return true;
            case OPTION_OPEN_IN:
                NeoMessageActions.openIn(chat, message);
                return true;
            case OPTION_QR:
                NeoMessageActions.scanQr(chat, message);
                return true;
            default:
                return false;
        }
    }
}
