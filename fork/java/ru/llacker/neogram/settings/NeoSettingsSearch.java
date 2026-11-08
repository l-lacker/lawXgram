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
                final String sectionTitle = NeoStrings.get(section.titleRes);
                results.add(new ProfileActivity.SearchAdapter.SearchResult(guid(section.slug), sectionTitle, app, 0,
                        () -> fragment.presentFragment(section.createFragment())).withLink(NeoSettings.link(section, null)));
                for (NeoSettingsRow row : section.rows()) {
                    if (!row.isSearchable() || !row.isVisible()) {
                        continue;
                    }
                    final String slug = row.slug;
                    results.add(new ProfileActivity.SearchAdapter.SearchResult(guid(section.slug + "/" + slug),
                            NeoStrings.get(row.titleRes), null, app, sectionTitle, 0,
                            () -> fragment.presentFragment(section.createFragment(slug))).withLink(NeoSettings.link(section, row)));
                }
            }
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
        return results.toArray(new ProfileActivity.SearchAdapter.SearchResult[0]);
    }

    static int guid(String path) {
        int hash = 0;
        for (int i = 0; i < path.length(); i++) {
            hash = 31 * hash + path.charAt(i);
        }
        return GUID_BASE | (hash & GUID_MASK);
    }
}
