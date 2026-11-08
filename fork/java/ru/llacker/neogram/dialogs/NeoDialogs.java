package ru.llacker.neogram.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Chat list (DialogsActivity) options: patches debloat/stories, feature/archive-on-pull and
 * feature/hide-all-chats-tab.
 */
public final class NeoDialogs {

    /** folder_id of the archive in TL dialogs. */
    private static final int ARCHIVE_FOLDER_ID = 1;
    /** Duration of the pull arrow's out animation (stock PullForegroundDrawable.startOutAnimation). */
    private static final long ARCHIVE_OPEN_DELAY_MS = 250;

    private NeoDialogs() {
    }

    // ---- stories ------------------------------------------------------------------------------

    /** The stories row, the "post a story" button and its hint are hidden. */
    public static boolean hideStories() {
        return NeoOptions.HIDE_STORIES.value();
    }

    // ---- archive ------------------------------------------------------------------------------

    /**
     * A pull revealed the hidden archive (stock state ARCHIVE_ITEM_STATE_SHOWED). With the option on,
     * the archive opens once the reveal animation has played, the same way a tap on its row opens it.
     */
    public static void onArchivePulled(@NonNull DialogsActivity activity) {
        if (!NeoOptions.ARCHIVE_ON_PULL.value()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            ActionBar actionBar = activity.getActionBar();
            if (activity.isFinished || activity.getParentActivity() == null || actionBar != null && actionBar.isActionModeShowed()) {
                return;
            }
            openArchive(activity);
        }, ARCHIVE_OPEN_DELAY_MS);
    }

    private static void openArchive(@NonNull BaseFragment from) {
        Bundle args = new Bundle();
        args.putInt("folderId", ARCHIVE_FOLDER_ID);
        from.presentFragment(new DialogsActivity(args));
    }

    // ---- folder tabs: All Chats -----------------------------------------------------------------

    public static boolean hideAllChatsTab() {
        return NeoOptions.HIDE_ALL_CHATS_TAB.value();
    }

    /**
     * All Chats is left out of the tab strip unless the tabs are being reordered: the stock reorder
     * code takes tab positions for folder indices, so every tab must be there while it runs.
     */
    public static boolean skipAllChatsTab(@NonNull FilterTabsView tabs) {
        return hideAllChatsTab() && !tabs.isEditing();
    }

    /** True when the strip shows All Chats although it should not, or the other way round. */
    public static boolean needsTabsRebuild(@Nullable FilterTabsView tabs, int filterCount) {
        if (tabs == null || tabs.isEmpty() || filterCount <= 1) {
            return false;
        }
        boolean allChatsShown = tabs.getDefaultTabId() >= 0;
        return allChatsShown == skipAllChatsTab(tabs);
    }

    /**
     * Selects, without animation, the first tab that is not locked (folders over the free limit
     * are locked and cannot be opened) and returns its id, which is the folder's index.
     */
    public static int selectFirstOpenTab(@NonNull FilterTabsView tabs) {
        FilterTabsView.Tab first = tabs.getTab(0);
        for (int i = 0; i < tabs.getTabsCount(); i++) {
            FilterTabsView.Tab tab = tabs.getTab(i);
            if (!tab.isLocked) {
                first = tab;
                break;
            }
        }
        if (first == null) {
            return 0;
        }
        tabs.selectTabWithStableId(tabs.getStableId(first.id));
        return first.id;
    }

    /**
     * Stock scrollToFolder takes a folder index for a tab position, which differs once All Chats
     * is left out. Returns the position of the folder's tab; an index the stock code did not find
     * a folder for (its "last tab" fallback) stays as it is.
     */
    public static int tabPosition(@NonNull FilterTabsView tabs, @NonNull ArrayList<MessagesController.DialogFilter> filters,
                                  int folderId, int index) {
        if (index < 0 || index >= filters.size() || filters.get(index).id != folderId) {
            return index;
        }
        for (int i = 0; i < tabs.getTabsCount(); i++) {
            if (tabs.getTab(i).id == index) {
                return i;
            }
        }
        return index;
    }

    /** Rebuilds the folder tabs of every chat list after the All Chats option changed. */
    public static void onFolderTabsOptionChanged() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
            }
        }
    }

    // ---- chat list menu (stock ApplicationLoader.addItemOptions) --------------------------------

    /** Without the All Chats tab the archive row is out of reach, so the chat list menu gets an item for it. */
    public static void addMenuItems(@NonNull ItemOptions options) {
        if (!hideAllChatsTab()) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        if (controller.getDialogFilters().size() <= 1 || controller.dialogs_dict.get(DialogObject.makeFolderDialogId(ARCHIVE_FOLDER_ID)) == null) {
            return;
        }
        options.add(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats), () -> {
            BaseFragment fragment = LaunchActivity.getLastFragmentIncludeMainTabs();
            if (fragment != null) {
                openArchive(fragment);
            }
        });
    }
}
