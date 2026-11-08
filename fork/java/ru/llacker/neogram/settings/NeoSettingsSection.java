package ru.llacker.neogram.settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.ArrayList;
import java.util.List;

/**
 * A page of the Neogram settings hub: identity (slug, title, icon), its rows and the fragment
 * that shows them. Sections are static descriptions, so search and deep links work without
 * creating any fragment.
 */
public final class NeoSettingsSection {

    public interface Rows {
        void build(@NonNull List<NeoSettingsRow> rows);
    }

    public interface FragmentFactory {
        @NonNull
        BaseFragment create(@NonNull NeoSettingsSection section, @Nullable String highlightSlug);
    }

    /** Stable, never renamed: part of tg://settings/neo/&lt;slug&gt; and of search history ids. */
    public final String slug;
    @StringRes
    public final int titleRes;
    @StringRes
    public final int subtitleRes;
    @DrawableRes
    public final int iconRes;
    public final IconBackgroundColors color;
    private final Rows rows;
    @Nullable
    private final FragmentFactory fragmentFactory;

    public NeoSettingsSection(@NonNull String slug, @StringRes int titleRes, @StringRes int subtitleRes,
                              @DrawableRes int iconRes, @NonNull IconBackgroundColors color, @NonNull Rows rows) {
        this(slug, titleRes, subtitleRes, iconRes, color, rows, null);
    }

    public NeoSettingsSection(@NonNull String slug, @StringRes int titleRes, @StringRes int subtitleRes,
                              @DrawableRes int iconRes, @NonNull IconBackgroundColors color, @NonNull Rows rows,
                              @Nullable FragmentFactory fragmentFactory) {
        this.slug = slug;
        this.titleRes = titleRes;
        this.subtitleRes = subtitleRes;
        this.iconRes = iconRes;
        this.color = color;
        this.rows = rows;
        this.fragmentFactory = fragmentFactory;
    }

    @NonNull
    public List<NeoSettingsRow> rows() {
        List<NeoSettingsRow> list = new ArrayList<>();
        rows.build(list);
        return list;
    }

    /** True when the section has at least one visible option (empty sections are hidden). */
    public boolean hasVisibleRows() {
        for (NeoSettingsRow row : rows()) {
            if (row.kind != NeoSettingsRow.Kind.HEADER && row.kind != NeoSettingsRow.Kind.INFO && row.isVisible()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public NeoSettingsRow findRow(@Nullable String rowSlug) {
        if (rowSlug == null) {
            return null;
        }
        for (NeoSettingsRow row : rows()) {
            if (rowSlug.equals(row.slug)) {
                return row;
            }
        }
        return null;
    }

    @NonNull
    public BaseFragment createFragment() {
        return createFragment(null);
    }

    @NonNull
    public BaseFragment createFragment(@Nullable String highlightSlug) {
        if (fragmentFactory != null) {
            return fragmentFactory.create(this, highlightSlug);
        }
        return new NeoSettingsPage(this).highlight(highlightSlug);
    }
}
