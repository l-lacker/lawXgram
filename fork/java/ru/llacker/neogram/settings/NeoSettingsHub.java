package ru.llacker.neogram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.NeoStrings;

/** Root page of the Neogram settings: the app header and one colored row per section. */
public class NeoSettingsHub extends UniversalFragment {

    private static final int SECTION_ID_BASE = 100;
    private final List<NeoSettingsSection> shown = new ArrayList<>();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected CharSequence getTitle() {
        return NeoBrand.appName();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopViewStatic(NeoStrings.get(R.string.NeoHubInfo), R.mipmap.ic_launcher));
        shown.clear();
        shown.addAll(NeoSettings.visibleSections());
        for (int i = 0; i < shown.size(); i++) {
            final NeoSettingsSection section = shown.get(i);
            items.add(SettingsActivity.SettingCell.Factory.of(SECTION_ID_BASE + i, section.color.top, section.color.bottom,
                    section.iconRes, NeoStrings.get(section.titleRes), NeoStrings.get(section.subtitleRes)));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - SECTION_ID_BASE;
        if (index >= 0 && index < shown.size()) {
            presentFragment(shown.get(index).createFragment());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }
}
