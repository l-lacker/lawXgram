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
