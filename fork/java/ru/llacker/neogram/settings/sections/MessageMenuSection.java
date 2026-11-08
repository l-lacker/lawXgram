package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** "Message menu" page (opened from Chats): which Neogram items the long-press menu offers. */
public final class MessageMenuSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("message_menu", R.string.NeoMessageMenu,
            R.string.NeoMessageMenuInfo, R.drawable.settings_chat, IconBackgroundColors.ORANGE, MessageMenuSection::rows);

    private MessageMenuSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoMessageMenuItems));
        rows.add(NeoSettingsRow.toggle("repeat", NeoOptions.MENU_REPEAT, R.string.NeoRepeat, R.string.NeoMenuRepeatInfo));
        rows.add(NeoSettingsRow.toggle("save", NeoOptions.MENU_SAVE, R.string.NeoAddToSavedMessages, R.string.NeoMenuSaveInfo));
        rows.add(NeoSettingsRow.toggle("copy_photo", NeoOptions.MENU_COPY_PHOTO, R.string.NeoCopyPhoto, R.string.NeoMenuCopyPhotoInfo));
        rows.add(NeoSettingsRow.toggle("delete_file", NeoOptions.MENU_DELETE_FILE, R.string.NeoDeleteDownloadedFile,
                R.string.NeoMenuDeleteFileInfo));
        rows.add(NeoSettingsRow.toggle("statistics", NeoOptions.MENU_STATISTICS, R.string.NeoMenuStatistics,
                R.string.NeoMenuStatisticsInfo));
        rows.add(NeoSettingsRow.toggle("details", NeoOptions.MENU_DETAILS, R.string.NeoMessageDetails, R.string.NeoMenuDetailsInfo));
        rows.add(NeoSettingsRow.toggle("report", NeoOptions.MENU_REPORT, R.string.NeoMenuReport, R.string.NeoMenuReportInfo));
    }
}
