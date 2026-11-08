package ru.llacker.neogram.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.settings.sections.AboutSection;
import ru.llacker.neogram.settings.sections.AccessibilitySection;
import ru.llacker.neogram.settings.sections.AdvancedSection;
import ru.llacker.neogram.settings.sections.AppearanceSection;
import ru.llacker.neogram.settings.sections.ChatsSection;
import ru.llacker.neogram.settings.sections.DialogsSection;
import ru.llacker.neogram.settings.sections.MediaSection;
import ru.llacker.neogram.settings.sections.PrivacySection;
import ru.llacker.neogram.settings.sections.TranslatorSection;

/**
 * The Neogram settings hub: one entry in the stock settings screen, one page per area, every
 * option in exactly one place. Also resolves {@code tg://settings/neo[/<section>[/<row>]]}.
 */
public final class NeoSettings {

    /** Item id of the hub row in the stock SettingsActivity (stock ids are small numbers). */
    public static final int HUB_ITEM_ID = 0x4E454F;

    private static volatile List<NeoSettingsSection> sections;

    private NeoSettings() {
    }

    /** All sections in hub order. */
    @NonNull
    public static List<NeoSettingsSection> sections() {
        List<NeoSettingsSection> list = sections;
        if (list == null) {
            synchronized (NeoSettings.class) {
                list = sections;
                if (list == null) {
                    list = Collections.unmodifiableList(Arrays.asList(
                            AppearanceSection.SECTION,
                            DialogsSection.SECTION,
                            ChatsSection.SECTION,
                            MediaSection.SECTION,
                            TranslatorSection.SECTION,
                            PrivacySection.SECTION,
                            AccessibilitySection.SECTION,
                            AdvancedSection.SECTION,
                            AboutSection.SECTION
                    ));
                    verify(list);
                    sections = list;
                }
            }
        }
        return list;
    }

    /** Sections that currently have something to show. */
    @NonNull
    public static List<NeoSettingsSection> visibleSections() {
        List<NeoSettingsSection> result = new ArrayList<>();
        for (NeoSettingsSection section : sections()) {
            if (section.hasVisibleRows()) {
                result.add(section);
            }
        }
        return result;
    }

    @Nullable
    public static NeoSettingsSection find(@Nullable String slug) {
        for (NeoSettingsSection section : sections()) {
            if (section.slug.equals(slug)) {
                return section;
            }
        }
        return null;
    }

    /** The row added to the stock settings screen (patch misc/settings-ui). */
    @NonNull
    public static UItem hubEntry() {
        return SettingsActivity.SettingCell.Factory.of(HUB_ITEM_ID, IconBackgroundColors.BLUE_DEEP.top,
                IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.neo_section_hub, NeoBrand.appName(),
                NeoStrings.get(R.string.NeoHubInfo));
    }

    @NonNull
    public static BaseFragment createHub() {
        return new NeoSettingsHub();
    }

    /**
     * Opens a hub link path (the segments after {@code tg://settings/neo}). Unknown sections fall
     * back to the hub, unknown rows to their section.
     */
    public static boolean openLink(@NonNull Consumer<BaseFragment> present, @NonNull List<String> segments) {
        if (segments.isEmpty()) {
            present.accept(createHub());
            return true;
        }
        final NeoSettingsSection section = find(segments.get(0));
        if (section == null) {
            present.accept(createHub());
            return true;
        }
        final String row = segments.size() > 1 ? segments.get(1) : null;
        present.accept(section.createFragment(section.findRow(row) != null ? row : null));
        return true;
    }

    public static String link(@NonNull NeoSettingsSection section, @Nullable NeoSettingsRow row) {
        return "tg://settings/neo/" + section.slug + (row != null && row.slug != null ? "/" + row.slug : "");
    }

    /** Slugs are part of links and of the search history: they must be unique and stable. */
    private static void verify(List<NeoSettingsSection> list) {
        Set<String> sectionSlugs = new HashSet<>();
        for (NeoSettingsSection section : list) {
            if (!sectionSlugs.add(section.slug)) {
                throw new IllegalStateException("duplicate settings section " + section.slug);
            }
            Set<String> rowSlugs = new HashSet<>();
            for (NeoSettingsRow row : section.rows()) {
                if (row.slug != null && !rowSlugs.add(row.slug)) {
                    throw new IllegalStateException("duplicate settings row " + section.slug + "/" + row.slug);
                }
            }
        }
    }
}
