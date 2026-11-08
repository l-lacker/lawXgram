package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.config.NeoValue;
import ru.llacker.neogram.dialogs.NeoDialogs;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "dialogs": the chat list, the archive and folder tabs. */
public final class DialogsSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("dialogs", R.string.NeoSectionDialogs,
            R.string.NeoSectionDialogsInfo, R.drawable.settings_folders, IconBackgroundColors.BLUE_ALT, DialogsSection::rows);

    private DialogsSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoChatListHeader));
        rows.add(NeoSettingsRow.toggle("hide_stories", NeoOptions.HIDE_STORIES,
                R.string.NeoHideStories, R.string.NeoHideStoriesInfo));
        rows.add(NeoSettingsRow.toggle("archive_on_pull", NeoOptions.ARCHIVE_ON_PULL,
                R.string.NeoOpenArchiveOnPull, R.string.NeoOpenArchiveOnPullInfo));
        rows.add(NeoSettingsRow.toggle("dialog_media_preview", NeoOptions.DIALOG_MEDIA_PREVIEW,
                R.string.NeoMediaPreview, R.string.NeoMediaPreviewInfo));

        rows.add(NeoSettingsRow.header(R.string.Filters));
        rows.add(NeoSettingsRow.toggle("hide_all_chats_tab", NeoValue.of(NeoOptions.HIDE_ALL_CHATS_TAB::get, hide -> {
                    NeoOptions.HIDE_ALL_CHATS_TAB.set(hide);
                    NeoDialogs.onFolderTabsOptionChanged();
                }), R.string.NeoHideAllTab, R.string.NeoHideAllTabInfo));
    }
}
