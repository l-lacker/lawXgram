package ru.llacker.neogram.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Double tap on a message (patch feature/double-tap): the stock reaction, or a Neogram action
 * chosen separately for incoming and outgoing messages.
 */
public final class NeoDoubleTap {

    public enum Action { REACTION, NONE, REPLY, SAVE, REPEAT, EDIT }

    private NeoDoubleTap() {
    }

    @Nullable
    private static MessageObject messageOf(@NonNull View view) {
        return view instanceof ChatMessageCell ? ((ChatMessageCell) view).getPrimaryMessageObject() : null;
    }

    @NonNull
    private static Action actionFor(@NonNull MessageObject message) {
        return message.isOut() ? NeoOptions.DOUBLE_TAP_OUT.get() : NeoOptions.DOUBLE_TAP_IN.get();
    }

    /** Null keeps the stock (reaction) decision. */
    @Nullable
    public static Boolean hasDoubleTap(@NonNull ChatActivity chat, @NonNull View view) {
        MessageObject message = messageOf(view);
        if (message == null || message.isDateObject) {
            return null;
        }
        Action action = actionFor(message);
        if (action == Action.REACTION) {
            return null;
        }
        if (action == Action.NONE || message.isSending() || message.isSponsored() || chat.isInScheduleMode()
                || chat.getChatMode() != ChatActivity.MODE_DEFAULT) {
            return false;
        }
        switch (action) {
            case REPLY:
                return message.getId() > 0;
            case SAVE:
                return NeoMessageActions.canSave(chat, message);
            case REPEAT:
                return NeoMessageActions.canRepeat(chat, message);
            case EDIT:
                return message.canEditMessage(chat.getCurrentChat());
            default:
                return false;
        }
    }

    /** True when the double tap was handled here (not a reaction). */
    public static boolean onDoubleTap(@NonNull ChatActivity chat, @NonNull View view) {
        MessageObject message = messageOf(view);
        if (message == null) {
            return false;
        }
        Action action = actionFor(message);
        if (action == Action.REACTION) {
            return false;
        }
        Boolean allowed = hasDoubleTap(chat, view);
        if (allowed == null || !allowed) {
            return true;
        }
        MessageObject.GroupedMessages group = ((ChatMessageCell) view).getCurrentMessagesGroup();
        switch (action) {
            case REPLY:
                chat.neo_runOption(message, group, ChatActivity.OPTION_REPLY);
                break;
            case SAVE:
                NeoMessageActions.saveToSavedMessages(chat, message, group);
                break;
            case REPEAT:
                NeoMessageActions.repeat(chat, message, group);
                break;
            case EDIT:
                chat.neo_runOption(message, group, ChatActivity.OPTION_EDIT);
                break;
            default:
                break;
        }
        return true;
    }
}
