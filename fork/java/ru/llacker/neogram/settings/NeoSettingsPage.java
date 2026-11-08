package ru.llacker.neogram.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.config.NeoValue;

/**
 * Renders a {@link NeoSettingsSection} with stock 13.x settings building blocks (sectioned list,
 * glass header): toggles with a description line, choices with the current value and a popup,
 * actions, headers and explanatory text. Pages with custom content subclass it.
 */
public class NeoSettingsPage extends UniversalFragment {

    private static final int ROW_ID_BASE = 10_000;

    protected final NeoSettingsSection section;
    private final List<NeoSettingsRow> rows = new ArrayList<>();

    /** Receives the result of an activity started with {@link #startForResult}. */
    public interface ResultCallback {
        void onResult(int resultCode, @Nullable Intent data);
    }

    /** Request codes of this page; far from the small numbers stock fragments use. */
    private static final int REQUEST_BASE = 0x4E00;
    private final SparseArray<ResultCallback> pendingResults = new SparseArray<>();
    private int nextRequest;
    @Nullable
    private String pendingHighlight;

    public NeoSettingsPage(@NonNull NeoSettingsSection section) {
        this.section = section;
    }

    /** Scroll to and flash the row with this slug once the page is shown (search, deep links). */
    public NeoSettingsPage highlight(@Nullable String rowSlug) {
        pendingHighlight = rowSlug;
        return this;
    }

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
        if (pendingHighlight != null) {
            final String slug = pendingHighlight;
            pendingHighlight = null;
            AndroidUtilities.runOnUIThread(() -> highlightRow(slug), 300);
        }
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
        return NeoStrings.get(section.titleRes);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        rows.addAll(section.rows());
        boolean groupOpen = false;
        for (int i = 0; i < rows.size(); i++) {
            final NeoSettingsRow row = rows.get(i);
            if (!row.isVisible()) {
                continue;
            }
            final int id = ROW_ID_BASE + i;
            switch (row.kind) {
                case HEADER:
                    if (groupOpen) {
                        items.add(UItem.asShadow(null));
                    }
                    items.add(UItem.asHeader(NeoStrings.get(row.titleRes)));
                    groupOpen = true;
                    break;
                case INFO:
                    items.add(UItem.asShadow(row.description()));
                    groupOpen = false;
                    break;
                case TOGGLE: {
                    final CharSequence title = NeoStrings.get(row.titleRes);
                    final CharSequence description = row.description();
                    final boolean checked = isChecked(row);
                    items.add(description != null
                            ? UItem.asButtonCheck(id, title, description).setChecked(checked)
                            : UItem.asCheck(id, title).setChecked(checked));
                    groupOpen = true;
                    break;
                }
                case CHOICE:
                    items.add(UItem.asButton(id, NeoStrings.get(row.titleRes), currentLabel(row)));
                    groupOpen = describe(items, row);
                    break;
                case ACTION: {
                    final CharSequence title = NeoStrings.get(row.titleRes);
                    final UItem item = row.iconRes != 0 ? UItem.asButton(id, row.iconRes, title) : UItem.asButton(id, title);
                    if (row.destructive) {
                        item.red();
                    }
                    items.add(item);
                    groupOpen = describe(items, row);
                    break;
                }
            }
        }
        if (groupOpen) {
            items.add(UItem.asShadow(null));
        }
    }

    /** Choice and action cells have no description line: their explanation closes the group. */
    private static boolean describe(ArrayList<UItem> items, NeoSettingsRow row) {
        final CharSequence description = row.description();
        if (description != null) {
            items.add(UItem.asShadow(description));
            return false;
        }
        return true;
    }

    private static boolean isChecked(NeoSettingsRow row) {
        return row.toggle != null && Boolean.TRUE.equals(row.toggle.get());
    }

    @Nullable
    private NeoSettingsRow rowFor(UItem item) {
        final int index = item.id - ROW_ID_BASE;
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final NeoSettingsRow row = rowFor(item);
        if (row == null) {
            return;
        }
        switch (row.kind) {
            case TOGGLE:
                if (row.toggle != null) {
                    row.toggle.set(!isChecked(row));
                    listView.adapter.update(true);
                }
                break;
            case CHOICE:
                showChoice(row, view);
                break;
            case ACTION:
                if (row.action != null) {
                    row.action.run(this, view);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /** Starts an activity (a document picker, for example) and hands its result to the callback. */
    public boolean startForResult(@NonNull Intent intent, @NonNull ResultCallback callback) {
        int requestCode = REQUEST_BASE + (nextRequest++ & 0xFF);
        pendingResults.put(requestCode, callback);
        try {
            startActivityForResult(intent, requestCode);
            return true;
        } catch (ActivityNotFoundException e) {
            pendingResults.remove(requestCode);
            return false;
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        ResultCallback callback = pendingResults.get(requestCode);
        if (callback != null) {
            pendingResults.remove(requestCode);
            callback.onResult(resultCode, data);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    /** Rebuilds the list, e.g. after an option changed outside of a click. */
    public void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @SuppressWarnings("unchecked")
    private void showChoice(NeoSettingsRow row, View anchor) {
        final NeoValue<Object> item = (NeoValue<Object>) row.choice;
        if (item == null || getParentActivity() == null) {
            return;
        }
        final Object current = item.get();
        final ItemOptions options = ItemOptions.makeOptions(this, anchor);
        for (NeoSettingsRow.Option<?> option : row.options) {
            final Object value = option.value;
            options.add(Objects.equals(value, current) ? R.drawable.msg_text_check : 0, label(option), () -> {
                item.set(value);
                refresh();
            });
        }
        options.show();
    }

    private static CharSequence label(NeoSettingsRow.Option<?> option) {
        return option.labelRes != 0 ? NeoStrings.get(option.labelRes) : option.label;
    }

    private static CharSequence currentLabel(NeoSettingsRow row) {
        final Object current = row.choice == null ? null : row.choice.get();
        for (NeoSettingsRow.Option<?> option : row.options) {
            if (Objects.equals(option.value, current)) {
                return label(option);
            }
        }
        return current == null ? "" : String.valueOf(current);
    }

    /** Scrolls to the row with this slug and flashes it. */
    public void highlightRow(@NonNull String rowSlug) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rowSlug.equals(rows.get(i).slug)) {
                final int position = listView.findPositionByItemId(ROW_ID_BASE + i);
                if (position >= 0) {
                    listView.highlightRow(() -> position);
                }
                return;
            }
        }
    }
}
