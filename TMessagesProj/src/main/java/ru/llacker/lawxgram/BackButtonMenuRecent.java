package ru.llacker.lawxgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.LinkedList;

import ru.llacker.lawxgram.helpers.PreferencesMigrationHelper;

public class BackButtonMenuRecent {

    private static final int MAX_RECENT_DIALOGS = 25;
    private static final String PREFS_NAME = "lawxrecentdialogs";
    private static final String LEGACY_PREFS_NAME = "nekorecentdialogs";
    private static final String PREFS_KEY_PREFIX = "recents_";
    private static final String PREFS_TOPICS_KEY_PREFIX = "recents_topics_";

    private static final SharedPreferences preferences = PreferencesMigrationHelper.getSharedPreferences(ApplicationLoader.applicationContext, PREFS_NAME, LEGACY_PREFS_NAME);
    private static final SparseArray<LinkedList<MessagesStorage.TopicKey>> recentDialogs = new SparseArray<>();

    public static boolean show(int currentAccount, BaseFragment fragment, View button, DialogsActivity.DialogsActivityDelegate delegate) {
        return show(currentAccount, fragment, button, delegate, 0, null, null);
    }

    public static boolean show(int currentAccount, BaseFragment fragment, View button, DialogsActivity.DialogsActivityDelegate delegate, int switchIconResId, CharSequence switchText, Runnable switchAction) {
        var context = fragment.getParentActivity();
        if (context == null) {
            return false;
        }
        var dialogs = getRecentDialogs(fragment.getCurrentAccount());
        boolean hasSwitchAction = switchAction != null && switchText != null;
        if (dialogs.isEmpty() && !hasSwitchAction) {
            return false;
        }
        var options = ItemOptions.makeOptions(fragment, button);
        if (!dialogs.isEmpty()) {
            options.add(R.drawable.menu_clear_recent, LocaleController.getString(R.string.ClearButton), () -> {
                var builder = new AlertDialog.Builder(context);
                builder.setTitle(LocaleController.getString(R.string.ClearRecentChats));
                builder.setMessage(LocaleController.getString(R.string.ClearRecentChatAlert));
                builder.setPositiveButton(LocaleController.getString(R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> clearRecentDialogs(currentAccount));
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                fragment.showDialog(builder.create());
            });
            options.addGap();
        }
        for (var recentDialog : dialogs) {
            final long dialogId = recentDialog.dialogId;
            final long topicId = recentDialog.topicId;
            final TLRPC.Chat chat;
            final TLRPC.User user;
            if (dialogId < 0) {
                chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                user = null;
            } else {
                chat = null;
                user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            }
            if (chat == null && user == null) {
                continue;
            }
            var cell = new FrameLayout(context);

            var imageView = new BackupImageView(context);
            imageView.setRoundRadius(chat != null && (chat.forum || ChatObject.isMonoForum(chat)) ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16));
            cell.addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 13, 0, 1, 0));

            var titleView = new TextView(context);
            titleView.setLines(1);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 59, 0, 12, 0));

            var avatarDrawable = new AvatarDrawable();
            avatarDrawable.setScaleSize(.8f);
            Drawable thumb = avatarDrawable;

            if (chat != null) {
                boolean monoForumTopic = topicId != 0 && ChatObject.isMonoForum(chat);
                avatarDrawable.setInfo(chat);
                if (monoForumTopic) {
                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId);
                    long topicPeerId = topic != null ? ForumUtilities.getMonoForumTopicPeerDialogId(topic) : topicId;
                    TLObject topicPeer = MessagesController.getInstance(currentAccount).getUserOrChat(topicPeerId);
                    imageView.setRoundRadius(AndroidUtilities.dp(16));
                    if (topicPeer instanceof TLRPC.User) {
                        TLRPC.User topicUser = (TLRPC.User) topicPeer;
                        avatarDrawable.setInfo(topicUser);
                        titleView.setText(UserObject.getUserName(topicUser));
                        imageView.setImage(ImageLocation.getForUser(topicUser, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, topicUser);
                    } else if (topicPeer instanceof TLRPC.Chat) {
                        TLRPC.Chat topicChat = (TLRPC.Chat) topicPeer;
                        avatarDrawable.setInfo(topicChat);
                        titleView.setText(topicChat.title);
                        imageView.setImage(ImageLocation.getForChat(topicChat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, topicChat);
                    } else {
                        ForumUtilities.setMonoForumAvatar(currentAccount, chat, avatarDrawable, imageView);
                        titleView.setText(ForumUtilities.getMonoForumTitle(currentAccount, chat, true));
                    }
                    var titleParams = (FrameLayout.LayoutParams) titleView.getLayoutParams();
                    if (LocaleController.isRTL) {
                        titleParams.leftMargin = AndroidUtilities.dp(40);
                    } else {
                        titleParams.rightMargin = AndroidUtilities.dp(40);
                    }
                    titleView.setLayoutParams(titleParams);

                    var badgeView = new ImageView(context);
                    Drawable badge = ContextCompat.getDrawable(context, R.drawable.filled_profile_message_24);
                    if (badge != null) {
                        badge = badge.mutate();
                        badge.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
                        badgeView.setImageDrawable(badge);
                    }
                    badgeView.setContentDescription(LocaleController.getString(R.string.ChannelOpenDirect));
                    cell.addView(badgeView, LayoutHelper.createFrameRelatively(18, 18, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
                } else if (chat.photo != null && chat.photo.strippedBitmap != null) {
                    thumb = chat.photo.strippedBitmap;
                    imageView.setImage(ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL), "50_50", thumb, chat);
                } else {
                    imageView.setImage(ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL), "50_50", thumb, chat);
                }
                if (!monoForumTopic && topicId != 0 && ChatObject.isForum(chat)) {
                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId);
                    if (topic != null) {
                        ForumUtilities.setTopicIcon(imageView, topic);
                    }
                    titleView.setText(topic != null ? topic.title : chat.title);
                } else if (ChatObject.isMonoForum(chat)) {
                    if (topicId == 0) {
                        titleView.setText(ForumUtilities.getMonoForumTitle(currentAccount, chat));
                    }
                } else {
                    titleView.setText(chat.title);
                }
            } else {
                String name;
                if (user.photo != null && user.photo.strippedBitmap != null) {
                    thumb = user.photo.strippedBitmap;
                }
                if (UserObject.isReplyUser(user)) {
                    name = LocaleController.getString(R.string.RepliesTitle);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isDeleted(user)) {
                    name = LocaleController.getString(R.string.HiddenName);
                    avatarDrawable.setInfo(user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
                } else {
                    name = UserObject.getUserName(user);
                    avatarDrawable.setInfo(user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", thumb, user);
                }
                if (topicId != 0) {
                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-dialogId, topicId);
                    if (topic != null) {
                        ForumUtilities.setTopicIcon(imageView, topic);
                        titleView.setText(topic.title);
                    } else {
                        titleView.setText(name);
                    }
                } else {
                    titleView.setText(name);
                }
            }

            cell.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), false));
            cell.setOnClickListener(e2 -> {
                options.dismiss();
                if (fragment instanceof DialogsActivity dialogsActivity && delegate != null) {
                    ArrayList<MessagesStorage.TopicKey> keys = new ArrayList<>();
                    keys.add(MessagesStorage.TopicKey.of(dialogId, topicId));
                    delegate.didSelectDialogs(dialogsActivity, keys, null, false, true, 0, 0, null);
                    return;
                }
                var bundle = new Bundle();
                if (dialogId < 0) {
                    bundle.putLong("chat_id", -dialogId);
                    boolean monoForum = ChatObject.isMonoForum(chat);
                    if (monoForum) {
                        bundle.putInt("chatMode", ChatActivity.MODE_SUGGESTIONS);
                        bundle.putBoolean("isSubscriberSuggestions", !ChatObject.canManageMonoForum(currentAccount, chat));
                    }
                    if (topicId != 0) {
                        ChatActivity chatActivity = new ChatActivity(bundle);
                        ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(dialogId, topicId));
                        fragment.presentFragment(chatActivity);
                    } else if (monoForum) {
                        fragment.presentFragment(new ChatActivity(bundle));
                    } else if (MessagesController.getInstance(currentAccount).isForum(dialogId)) {
                        fragment.presentFragment(new TopicsFragment(bundle));
                    } else {
                        fragment.presentFragment(new ChatActivity(bundle));
                    }
                } else {
                    bundle.putLong("user_id", dialogId);
                    ChatActivity chatActivity = new ChatActivity(bundle);
                    if (topicId != 0) {
                        ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(dialogId, topicId));
                    }
                    fragment.presentFragment(chatActivity);
                }
            });
            cell.setOnLongClickListener(e2 -> {
                options.dismiss();
                var bundle = new Bundle();
                long profileDialogId = dialogId;
                if (topicId != 0 && chat != null && ChatObject.isMonoForum(chat)) {
                    TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId);
                    long topicPeerId = topic != null ? ForumUtilities.getMonoForumTopicPeerDialogId(topic) : topicId;
                    if (topicPeerId != 0) {
                        profileDialogId = topicPeerId;
                    }
                }
                if (profileDialogId < 0) {
                    bundle.putLong("chat_id", -profileDialogId);
                } else {
                    bundle.putLong("user_id", profileDialogId);
                }
                fragment.presentFragment(new ProfileActivity(bundle));
                return true;
            });
            options.addView(cell, LayoutHelper.createLinear(230, 48));
        }
        if (hasSwitchAction) {
            if (!dialogs.isEmpty()) {
                options.addGap();
            }
            var switchItem = new ActionBarMenuSubItem(context, false, false, fragment.getResourceProvider());
            switchItem.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            switchItem.setTextAndIcon(switchText, switchIconResId);
            switchItem.setOnClickListener(e -> {
                options.dismiss();
                AndroidUtilities.runOnUIThread(switchAction);
            });
            options.addView(switchItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (fragment instanceof MainTabsActivity) {
            options.setBlur(true);
            options.translate(0, -AndroidUtilities.dp(4));
            var bg = Theme.createRoundRectDrawable(AndroidUtilities.dp(28), Theme.getColor(Theme.key_windowBackgroundWhite));
            bg.getPaint().setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(1), Theme.multAlpha(0xFF000000, 0.15f));
            options.setScrimViewBackground(bg);
        } else {
            options.setScrimViewBackground(Theme.createCircleDrawable(AndroidUtilities.dp(40), Theme.getColor(Theme.key_windowBackgroundWhite)));
        }
        options.show();
        return true;
    }

    private static LinkedList<MessagesStorage.TopicKey> getRecentDialogs(int currentAccount) {
        LinkedList<MessagesStorage.TopicKey> recentDialog = recentDialogs.get(currentAccount);
        if (recentDialog == null) {
            recentDialog = new LinkedList<>();
            String list = preferences.getString(PREFS_TOPICS_KEY_PREFIX + currentAccount, null);
            if (!TextUtils.isEmpty(list)) {
                readRecentDialogs(list, recentDialog, true);
            } else {
                list = preferences.getString(PREFS_KEY_PREFIX + currentAccount, null);
                if (!TextUtils.isEmpty(list)) {
                    readRecentDialogs(list, recentDialog, false);
                    saveRecentDialogsAsync(currentAccount, recentDialog);
                }
            }
            recentDialogs.put(currentAccount, recentDialog);
        }
        return recentDialog;
    }

    private static void readRecentDialogs(String list, LinkedList<MessagesStorage.TopicKey> recentDialog, boolean hasTopicIds) {
        SerializedData data = null;
        try {
            byte[] bytes = Base64.decode(list, Base64.NO_WRAP | Base64.NO_PADDING);
            data = new SerializedData(bytes);
            if (data.remaining() < 4) {
                return;
            }
            int count = data.readInt32(false);
            for (int a = 0; a < count && data.remaining() >= 8; a++) {
                long dialogId = data.readInt64(false);
                long topicId = 0;
                if (hasTopicIds) {
                    if (data.remaining() < 8) {
                        break;
                    }
                    topicId = data.readInt64(false);
                }
                recentDialog.add(MessagesStorage.TopicKey.of(dialogId, topicId));
            }
        } catch (Exception ignore) {
            recentDialog.clear();
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    public static void addToRecentDialogs(int currentAccount, long dialogId) {
        addToRecentDialogs(currentAccount, dialogId, 0);
    }

    public static void addToRecentDialogs(int currentAccount, long dialogId, long topicId) {
        LinkedList<MessagesStorage.TopicKey> recentDialog = getRecentDialogs(currentAccount);
        if (!recentDialog.isEmpty() && recentDialog.getFirst().dialogId == dialogId && recentDialog.getFirst().topicId == topicId) {
            if (recentDialog.size() <= MAX_RECENT_DIALOGS) {
                return;
            }
            trimRecentDialogs(recentDialog);
            saveRecentDialogsAsync(currentAccount, recentDialog);
            return;
        }
        for (int i = 0; i < recentDialog.size(); i++) {
            MessagesStorage.TopicKey key = recentDialog.get(i);
            if (key.dialogId == dialogId && key.topicId == topicId) {
                recentDialog.remove(i);
                break;
            }
        }

        recentDialog.addFirst(MessagesStorage.TopicKey.of(dialogId, topicId));
        trimRecentDialogs(recentDialog);
        saveRecentDialogsAsync(currentAccount, recentDialog);
    }

    private static void trimRecentDialogs(LinkedList<MessagesStorage.TopicKey> recentDialog) {
        while (recentDialog.size() > MAX_RECENT_DIALOGS) {
            recentDialog.removeLast();
        }
    }

    private static void saveRecentDialogsAsync(int currentAccount, LinkedList<MessagesStorage.TopicKey> recentDialog) {
        LinkedList<MessagesStorage.TopicKey> finalRecentDialog = new LinkedList<>();
        for (MessagesStorage.TopicKey topicKey : recentDialog) {
            finalRecentDialog.add(MessagesStorage.TopicKey.of(topicKey.dialogId, topicKey.topicId));
        }
        Utilities.globalQueue.postRunnable(() -> saveRecentDialogs(currentAccount, finalRecentDialog));
    }

    private static void saveRecentDialogs(int currentAccount, LinkedList<MessagesStorage.TopicKey> recentDialog) {
        SerializedData serializedData = new SerializedData();
        int count = recentDialog.size();
        serializedData.writeInt32(count);
        for (MessagesStorage.TopicKey dialog : recentDialog) {
            serializedData.writeInt64(dialog.dialogId);
            serializedData.writeInt64(dialog.topicId);
        }
        preferences.edit().putString(PREFS_TOPICS_KEY_PREFIX + currentAccount, Base64.encodeToString(serializedData.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING)).apply();
        serializedData.cleanup();
    }

    public static void clearRecentDialogs(int currentAccount) {
        getRecentDialogs(currentAccount).clear();
        preferences.edit()
                .putString(PREFS_KEY_PREFIX + currentAccount, "")
                .putString(PREFS_TOPICS_KEY_PREFIX + currentAccount, "")
                .apply();
    }
}
