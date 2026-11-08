package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.config.NeoValue;
import ru.llacker.neogram.profile.NeoProfile;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "appearance": how times, numbers and the interface look. */
public final class AppearanceSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("appearance", R.string.NeoSectionAppearance,
            R.string.NeoSectionAppearanceInfo, R.drawable.neo_section_appearance, IconBackgroundColors.PURPLE, AppearanceSection::rows);

    private AppearanceSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoInterfaceHeader));
        rows.add(NeoSettingsRow.toggle("hide_app_bar_shadow", NeoOptions.HIDE_APP_BAR_SHADOW,
                R.string.NeoDisableAppBarShadow, R.string.NeoDisableAppBarShadowInfo));

        rows.add(NeoSettingsRow.header(R.string.NeoTimesAndNumbers));
        rows.add(NeoSettingsRow.toggle("time_with_seconds", NeoOptions.TIME_WITH_SECONDS,
                R.string.NeoFormatWithSeconds, R.string.NeoFormatWithSecondsInfo));
        rows.add(NeoSettingsRow.toggle("exact_numbers", NeoOptions.EXACT_NUMBERS,
                R.string.NeoDisableNumberRounding, R.string.NeoDisableNumberRoundingInfo));
        rows.add(NeoSettingsRow.choice("name_order", NeoValue.of(NeoOptions.NAME_ORDER::get, order -> {
                    NeoOptions.NAME_ORDER.set(order);
                    LocaleController.getInstance().recreateFormatters();
                }), R.string.NeoNameOrder, R.string.NeoNameOrderInfo)
                .option(NeoOptions.NameOrder.AUTO, R.string.NeoNameOrderAuto)
                .option(NeoOptions.NameOrder.FIRST_LAST, R.string.NeoFirstLast)
                .option(NeoOptions.NameOrder.LAST_FIRST, R.string.NeoLastFirst)
                .build());

        rows.add(NeoSettingsRow.header(R.string.NeoProfilesHeader));
        rows.add(NeoSettingsRow.choice("profile_id", NeoOptions.PROFILE_ID, R.string.NeoIdType, R.string.NeoIdTypeAbout)
                .option(NeoProfile.IdFormat.API, R.string.NeoIdTypeAPI)
                .option(NeoProfile.IdFormat.BOT_API, R.string.NeoIdTypeBOTAPI)
                .option(NeoProfile.IdFormat.HIDDEN, R.string.NeoIdTypeHidden)
                .build());
    }
}
