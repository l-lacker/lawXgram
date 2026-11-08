package ru.llacker.neogram.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.llacker.neogram.config.NeoConfigItem;

/**
 * One row of a Neogram settings page. Rows are declarative so that the same description renders
 * the page, feeds the stock settings search and resolves deep links. Every option row has a stable
 * {@link #slug} (never renamed: it is part of {@code tg://settings/neo/<page>/<slug>} links and of
 * the search history) and a description, so no option is left unexplained.
 */
public final class NeoSettingsRow {

    public enum Kind { HEADER, TOGGLE, CHOICE, ACTION, INFO }

    /** Opens something from a row (a sub page, a dialog, a URL). */
    public interface Action {
        void run(@NonNull NeoSettingsPage page, @Nullable android.view.View view);
    }

    /** Hides a row when the feature is unavailable (build type, Android version, device). */
    public interface Condition {
        boolean isMet();
    }

    /** Label of one value of a {@link Kind#CHOICE} row. */
    public static final class Option<T> {
        public final T value;
        @StringRes
        public final int labelRes;
        @Nullable
        public final CharSequence label;

        Option(T value, int labelRes, @Nullable CharSequence label) {
            this.value = value;
            this.labelRes = labelRes;
            this.label = label;
        }
    }

    public final Kind kind;
    @Nullable
    public final String slug;
    @StringRes
    public final int titleRes;
    @StringRes
    public final int descriptionRes;
    public final int iconRes;
    @Nullable
    public final NeoConfigItem.BoolItem toggle;
    @Nullable
    public final NeoConfigItem<?> choiceItem;
    public final List<Option<?>> options;
    @Nullable
    public final Action action;
    @Nullable
    public final Condition condition;
    public final boolean destructive;

    private NeoSettingsRow(Kind kind, String slug, int titleRes, int descriptionRes, int iconRes,
                           NeoConfigItem.BoolItem toggle, NeoConfigItem<?> choiceItem, List<Option<?>> options,
                           Action action, Condition condition, boolean destructive) {
        this.kind = kind;
        this.slug = slug;
        this.titleRes = titleRes;
        this.descriptionRes = descriptionRes;
        this.iconRes = iconRes;
        this.toggle = toggle;
        this.choiceItem = choiceItem;
        this.options = options;
        this.action = action;
        this.condition = condition;
        this.destructive = destructive;
    }

    public boolean isVisible() {
        return condition == null || condition.isMet();
    }

    /** Rows that are reachable from search and deep links. */
    public boolean isSearchable() {
        return slug != null && titleRes != 0;
    }

    public NeoSettingsRow onlyIf(@NonNull Condition condition) {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choiceItem, options, action,
                condition, destructive);
    }

    public NeoSettingsRow withIcon(int iconRes) {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choiceItem, options, action,
                condition, destructive);
    }

    public NeoSettingsRow asDestructive() {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choiceItem, options, action,
                condition, true);
    }

    // ---- factories ----------------------------------------------------------------------------

    public static NeoSettingsRow header(@StringRes int titleRes) {
        return new NeoSettingsRow(Kind.HEADER, null, titleRes, 0, 0, null, null, Collections.emptyList(), null, null, false);
    }

    /** Explanatory text under a group of rows. */
    public static NeoSettingsRow info(@StringRes int textRes) {
        return new NeoSettingsRow(Kind.INFO, null, 0, textRes, 0, null, null, Collections.emptyList(), null, null, false);
    }

    public static NeoSettingsRow toggle(@NonNull String slug, @NonNull NeoConfigItem.BoolItem item,
                                        @StringRes int titleRes, @StringRes int descriptionRes) {
        return new NeoSettingsRow(Kind.TOGGLE, slug, titleRes, descriptionRes, 0, item, null, Collections.emptyList(),
                null, null, false);
    }

    public static <T> ChoiceBuilder<T> choice(@NonNull String slug, @NonNull NeoConfigItem<T> item,
                                              @StringRes int titleRes, @StringRes int descriptionRes) {
        return new ChoiceBuilder<>(slug, item, titleRes, descriptionRes);
    }

    public static NeoSettingsRow action(@NonNull String slug, @StringRes int titleRes, @StringRes int descriptionRes,
                                        @NonNull Action action) {
        return new NeoSettingsRow(Kind.ACTION, slug, titleRes, descriptionRes, 0, null, null, Collections.emptyList(),
                action, null, false);
    }

    /** A row opening another page. */
    public static NeoSettingsRow page(@NonNull String slug, @NonNull NeoSettingsSection target,
                                      @StringRes int descriptionRes) {
        return action(slug, target.titleRes, descriptionRes, (page, view) -> page.presentFragment(target.createFragment()));
    }

    public static final class ChoiceBuilder<T> {
        private final String slug;
        private final NeoConfigItem<T> item;
        private final int titleRes;
        private final int descriptionRes;
        private final List<Option<?>> options = new ArrayList<>();

        ChoiceBuilder(String slug, NeoConfigItem<T> item, int titleRes, int descriptionRes) {
            this.slug = slug;
            this.item = item;
            this.titleRes = titleRes;
            this.descriptionRes = descriptionRes;
        }

        public ChoiceBuilder<T> option(T value, @StringRes int labelRes) {
            options.add(new Option<>(value, labelRes, null));
            return this;
        }

        /** An option whose label is not translatable (numbers, units, proper names). */
        public ChoiceBuilder<T> option(T value, @NonNull CharSequence label) {
            options.add(new Option<>(value, 0, label));
            return this;
        }

        public NeoSettingsRow build() {
            if (options.isEmpty()) {
                throw new IllegalStateException("choice " + slug + " has no options");
            }
            return new NeoSettingsRow(Kind.CHOICE, slug, titleRes, descriptionRes, 0, null, item,
                    Collections.unmodifiableList(new ArrayList<>(options)), null, null, false);
        }
    }
}
