package ru.llacker.neogram.settings;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.NeoStrings;

/** Feeds the Neogram hub, its sections and options into the stock settings search. */
public final class NeoSettingsSearch {

    /**
     * Search result ids are stored in the recent-searches history, so they must stay stable and
     * never collide with stock ids (small numbers): Neogram uses 0x4E000000 + a hash of the path.
     */
    private static final int GUID_BASE = 0x4E000000;
    private static final int GUID_MASK = 0x00FFFFFF;

    private NeoSettingsSearch() {
    }

    /** Called around ProfileActivity.SearchAdapter.onCreateSearchArray (patch misc/settings-ui). */
    public static ProfileActivity.SearchAdapter.SearchResult[] extend(ProfileActivity.SearchAdapter.SearchResult[] stock,
                                                                       @NonNull BaseFragment fragment) {
        final List<ProfileActivity.SearchAdapter.SearchResult> results = new ArrayList<>(Arrays.asList(stock));
        try {
            final String app = NeoBrand.appName();
            results.add(new ProfileActivity.SearchAdapter.SearchResult(guid("hub"), app, R.drawable.msg_settings_premium,
                    () -> fragment.presentFragment(NeoSettings.createHub())).withLink("tg://settings/neo"));
            for (NeoSettingsSection section : NeoSettings.visibleSections()) {
                addSection(results, section, app, fragment);
            }
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
        return results.toArray(new ProfileActivity.SearchAdapter.SearchResult[0]);
    }

    /** The section, its rows and the pages its rows open; {@code parent} is the path shown above. */
    private static void addSection(List<ProfileActivity.SearchAdapter.SearchResult> results, NeoSettingsSection section,
                                   String parent, BaseFragment fragment) {
        final String sectionTitle = NeoStrings.get(section.titleRes);
        results.add(new ProfileActivity.SearchAdapter.SearchResult(guid(section.slug), sectionTitle, parent, 0,
                () -> fragment.presentFragment(section.createFragment())).withLink(NeoSettings.link(section, null)));
        for (NeoSettingsRow row : section.rows()) {
            if (!row.isVisible()) {
                continue;
            }
            if (row.target != null) {
                addSection(results, row.target, sectionTitle, fragment);
                continue;
            }
            if (!row.isSearchable()) {
                continue;
            }
            final String slug = row.slug;
            results.add(new ProfileActivity.SearchAdapter.SearchResult(guid(section.slug + "/" + slug),
                    NeoStrings.get(row.titleRes), null, parent, sectionTitle, 0,
                    () -> fragment.presentFragment(section.createFragment(slug))).withLink(NeoSettings.link(section, row)));
        }
    }

    static int guid(String path) {
        int hash = 0;
        for (int i = 0; i < path.length(); i++) {
            hash = 31 * hash + path.charAt(i);
        }
        return GUID_BASE | (hash & GUID_MASK);
    }
}
