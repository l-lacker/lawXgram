package ru.llacker.neogram.config;

import ru.llacker.neogram.core.round.LensMath;

/**
 * Every Neogram option, grouped by settings section. Keys are snake_case and never change once
 * released (they are stored on devices and in backups). Read in Java as {@code NeoOptions.X.get()}
 * (or {@code .value()} for primitives). Options that the stock app already stores (round video V2,
 * audio recorder V2) live in {@code org.telegram.utils.settings.SharedSettings}, not here.
 */
public final class NeoOptions {

    private NeoOptions() {
    }

    /** Forces class initialization so every option is registered in {@link NeoConfig}. */
    static void ensureLoaded() {
    }

    // ---- Appearance ------------------------------------------------------------------------------

    /** Message and chat list times with seconds (the stock "HH:mm:ss" formatter). */
    public static final NeoConfigItem.BoolItem TIME_WITH_SECONDS = NeoConfig.bool("time_with_seconds", false);
    /** Exact view, member and reaction counts instead of 12.3K. */
    public static final NeoConfigItem.BoolItem EXACT_NUMBERS = NeoConfig.bool("exact_numbers", false);

    public enum NameOrder { AUTO, FIRST_LAST, LAST_FIRST }

    /** Order of first and last names; AUTO follows the app language (stock). */
    public static final NeoConfigItem.EnumItem<NameOrder> NAME_ORDER = NeoConfig.choice("name_order", NameOrder.AUTO);

    // ---- Chats: message menu and double tap ---------------------------------------------------------

    /** "Repeat" (+1): forward the message into the same chat. */
    public static final NeoConfigItem.BoolItem MENU_REPEAT = NeoConfig.bool("menu_repeat", true);
    /** "Save Message": forward to Saved Messages in one tap. */
    public static final NeoConfigItem.BoolItem MENU_SAVE = NeoConfig.bool("menu_save", true);
    public static final NeoConfigItem.BoolItem MENU_COPY_PHOTO = NeoConfig.bool("menu_copy_photo", false);
    public static final NeoConfigItem.BoolItem MENU_DELETE_FILE = NeoConfig.bool("menu_delete_file", false);
    public static final NeoConfigItem.BoolItem MENU_STATISTICS = NeoConfig.bool("menu_statistics", false);
    public static final NeoConfigItem.BoolItem MENU_DETAILS = NeoConfig.bool("menu_details", false);
    /** The stock "Report" item; off hides it. */
    public static final NeoConfigItem.BoolItem MENU_REPORT = NeoConfig.bool("menu_report", true);
    /** Double tap on incoming / outgoing messages; REACTION is the stock behaviour. */
    public static final NeoConfigItem.EnumItem<ru.llacker.neogram.chat.NeoDoubleTap.Action> DOUBLE_TAP_IN =
            NeoConfig.choice("double_tap_in", ru.llacker.neogram.chat.NeoDoubleTap.Action.REACTION);
    public static final NeoConfigItem.EnumItem<ru.llacker.neogram.chat.NeoDoubleTap.Action> DOUBLE_TAP_OUT =
            NeoConfig.choice("double_tap_out", ru.llacker.neogram.chat.NeoDoubleTap.Action.REACTION);

    // ---- Privacy -----------------------------------------------------------------------------------

    /** Confirm before starting a voice or video call (as in lawXgram, on by default). */
    public static final NeoConfigItem.BoolItem ASK_BEFORE_CALL = NeoConfig.bool("ask_before_call", true);
    /** Private chats with people who are not contacts notify without sound. */
    public static final NeoConfigItem.BoolItem SILENCE_NON_CONTACTS = NeoConfig.bool("silence_non_contacts", false);

    // ---- Media: video messages ---------------------------------------------------------------

    public enum RoundCamera { LAST, FRONT, BACK }

    public enum FastCameraSwitch { AUTO, OFF }

    public static final NeoConfigItem.EnumItem<RoundCamera> ROUND_START_CAMERA = NeoConfig.choice("round_start_camera", RoundCamera.LAST);
    /** Lens of the back camera a video message starts with (zoom-ratio lenses of the main camera). */
    public static final NeoConfigItem.EnumItem<LensMath.Kind> ROUND_START_LENS = NeoConfig.choice("round_start_lens", LensMath.Kind.MAIN);
    /** Show the lens button (0.6 / 1× / 2×) while recording when the back camera has several lenses. */
    public static final NeoConfigItem.BoolItem ROUND_LENS_SWITCHER = NeoConfig.bool("round_lens_switcher", true);
    /** Keep the other camera ready for an instant front/back switch (costs battery). */
    public static final NeoConfigItem.EnumItem<FastCameraSwitch> ROUND_FAST_SWITCH = NeoConfig.choice("round_fast_switch", FastCameraSwitch.AUTO);
    /** Derive the bitrate from resolution and frame rate instead of the manual value. */
    public static final NeoConfigItem.BoolItem ROUND_AUTO_BITRATE = NeoConfig.bool("round_auto_bitrate", true);
    /** Consecutive start failures of the new recorder (see NeoCameraPolicy); device-specific. */
    public static final NeoConfigItem.IntItem ROUND_V2_FAILURES = NeoConfig.integer("round_v2_failures", 0).notExportable();
    /** Firmware and app version the failure counter belongs to: an update gives the recorder a new chance. */
    public static final NeoConfigItem.StringItem ROUND_V2_FAILURES_BUILD = NeoConfig.string("round_v2_failures_build", "").notExportable();
    /** Time of the last counted failure, epoch millis: old failures are forgotten. */
    public static final NeoConfigItem.LongItem ROUND_V2_LAST_FAILURE = NeoConfig.longValue("round_v2_last_failure", 0L).notExportable();

    // ---- About / updates ----------------------------------------------------------------------

    /** Check GitHub releases for a newer direct build on start (at most once per interval). */
    public static final NeoConfigItem.BoolItem AUTO_UPDATE_CHECK = NeoConfig.bool("auto_update_check", true);
    /** Last successful automatic update check, epoch millis; device-specific. */
    public static final NeoConfigItem.LongItem LAST_UPDATE_CHECK = NeoConfig.longValue("last_update_check", 0L).notExportable();
    /** Release tag the user chose to skip. */
    public static final NeoConfigItem.StringItem SKIPPED_UPDATE = NeoConfig.string("skipped_update", "").notExportable();
}
