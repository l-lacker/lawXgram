package ru.llacker.neogram.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.llacker.neogram.config.NeoValue;

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

    /** Text under a row that depends on the current state; null falls back to the description. */
    public interface Describer {
        @Nullable
        CharSequence describe();
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
    /** Value of a {@link Kind#TOGGLE} row. */
    @Nullable
    public final NeoValue<Boolean> toggle;
    /** Value of a {@link Kind#CHOICE} row. */
    @Nullable
    public final NeoValue<?> choice;
    public final List<Option<?>> options;
    @Nullable
    public final Action action;
    /** The page a page row opens: its rows are searchable and linkable too. */
    @Nullable
    public final NeoSettingsSection target;
    @Nullable
    public final Condition condition;
    @Nullable
    public final Describer describer;
    public final boolean destructive;

    private NeoSettingsRow(Kind kind, String slug, int titleRes, int descriptionRes, int iconRes,
                           NeoValue<Boolean> toggle, NeoValue<?> choice, List<Option<?>> options, Action action,
                           NeoSettingsSection target, Condition condition, Describer describer, boolean destructive) {
        this.kind = kind;
        this.slug = slug;
        this.titleRes = titleRes;
        this.descriptionRes = descriptionRes;
        this.iconRes = iconRes;
        this.toggle = toggle;
        this.choice = choice;
        this.options = options;
        this.action = action;
        this.target = target;
        this.condition = condition;
        this.describer = describer;
        this.destructive = destructive;
    }

    public boolean isVisible() {
        return condition == null || condition.isMet();
    }

    /** Rows that are reachable from search and deep links. */
    public boolean isSearchable() {
        return slug != null && titleRes != 0;
    }

    /** The current explanation under the row, or null if it has none. */
    @Nullable
    public CharSequence description() {
        CharSequence text = describer == null ? null : describer.describe();
        if (text == null && descriptionRes != 0) {
            text = ru.llacker.neogram.NeoStrings.get(descriptionRes);
        }
        return text;
    }

    public NeoSettingsRow onlyIf(@NonNull Condition condition) {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choice, options, action, target,
                condition, describer, destructive);
    }

    public NeoSettingsRow describedBy(@NonNull Describer describer) {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choice, options, action, target,
                condition, describer, destructive);
    }

    public NeoSettingsRow withIcon(int iconRes) {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choice, options, action, target,
                condition, describer, destructive);
    }

    public NeoSettingsRow asDestructive() {
        return new NeoSettingsRow(kind, slug, titleRes, descriptionRes, iconRes, toggle, choice, options, action, target,
                condition, describer, true);
    }

    // ---- factories ----------------------------------------------------------------------------

    public static NeoSettingsRow header(@StringRes int titleRes) {
        return new NeoSettingsRow(Kind.HEADER, null, titleRes, 0, 0, null, null, Collections.emptyList(), null, null,
                null, null, false);
    }

    /** Explanatory text under a group of rows. */
    public static NeoSettingsRow info(@StringRes int textRes) {
        return new NeoSettingsRow(Kind.INFO, null, 0, textRes, 0, null, null, Collections.emptyList(), null, null,
                null, null, false);
    }

    public static NeoSettingsRow toggle(@NonNull String slug, @NonNull NeoValue<Boolean> value,
                                        @StringRes int titleRes, @StringRes int descriptionRes) {
        return new NeoSettingsRow(Kind.TOGGLE, slug, titleRes, descriptionRes, 0, value, null, Collections.emptyList(),
                null, null, null, null, false);
    }

    public static <T> ChoiceBuilder<T> choice(@NonNull String slug, @NonNull NeoValue<T> value,
                                              @StringRes int titleRes, @StringRes int descriptionRes) {
        return new ChoiceBuilder<>(slug, value, titleRes, descriptionRes);
    }

    public static NeoSettingsRow action(@NonNull String slug, @StringRes int titleRes, @StringRes int descriptionRes,
                                        @NonNull Action action) {
        return new NeoSettingsRow(Kind.ACTION, slug, titleRes, descriptionRes, 0, null, null, Collections.emptyList(),
                action, null, null, null, false);
    }

    /** A row opening another page; hidden while that page has nothing to show. */
    public static NeoSettingsRow page(@NonNull String slug, @NonNull NeoSettingsSection target,
                                      @StringRes int descriptionRes) {
        return new NeoSettingsRow(Kind.ACTION, slug, target.titleRes, descriptionRes, 0, null, null,
                Collections.emptyList(), (page, view) -> page.presentFragment(target.createFragment()), target,
                target::hasVisibleRows, null, false);
    }

    public static final class ChoiceBuilder<T> {
        private final String slug;
        private final NeoValue<T> value;
        private final int titleRes;
        private final int descriptionRes;
        private final List<Option<?>> options = new ArrayList<>();

        ChoiceBuilder(String slug, NeoValue<T> value, int titleRes, int descriptionRes) {
            this.slug = slug;
            this.value = value;
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
            return new NeoSettingsRow(Kind.CHOICE, slug, titleRes, descriptionRes, 0, null, value,
                    Collections.unmodifiableList(new ArrayList<>(options)), null, null, null, null, false);
        }
    }
}
