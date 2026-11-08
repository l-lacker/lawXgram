package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "privacy": calls, accounts and what others see. */
public final class PrivacySection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("privacy", R.string.NeoSectionPrivacy,
            R.string.NeoSectionPrivacyInfo, R.drawable.settings_privacy, IconBackgroundColors.GREEN, PrivacySection::rows);

    private PrivacySection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoCallsHeader));
        rows.add(NeoSettingsRow.toggle("ask_before_call", NeoOptions.ASK_BEFORE_CALL,
                R.string.NeoAskBeforeCalling, R.string.NeoAskBeforeCallingInfo));

        rows.add(NeoSettingsRow.header(R.string.NeoNotificationsHeader));
        rows.add(NeoSettingsRow.toggle("silence_non_contacts", NeoOptions.SILENCE_NON_CONTACTS,
                R.string.NeoSilenceNonContacts, R.string.NeoSilenceNonContactsAbout));
    }
}
