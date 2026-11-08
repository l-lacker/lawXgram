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
    /** The ID row in profiles and how it is written (shown in the Telegram API form by default, as in lawXgram). */
    public static final NeoConfigItem.EnumItem<ru.llacker.neogram.profile.NeoProfile.IdFormat> PROFILE_ID =
            NeoConfig.choice("profile_id", ru.llacker.neogram.profile.NeoProfile.IdFormat.API);

    /** No shadow under title bars. */
    public static final NeoConfigItem.BoolItem HIDE_APP_BAR_SHADOW = NeoConfig.bool("hide_app_bar_shadow", false);

    // ---- Chat list and folders ------------------------------------------------------------------

    /** No stories row above the chat list and in the archive, no "post a story" button and hint. */
    public static final NeoConfigItem.BoolItem HIDE_STORIES = NeoConfig.bool("hide_stories", false);
    /** Pulling the chat list down past the hidden archive opens the archive instead of only revealing it. */
    public static final NeoConfigItem.BoolItem ARCHIVE_ON_PULL = NeoConfig.bool("archive_on_pull", false);
    /** Thumbnails of the last message's photo or video in chat list rows (stock). */
    public static final NeoConfigItem.BoolItem DIALOG_MEDIA_PREVIEW = NeoConfig.bool("dialog_media_preview", true);
    /** Folder tabs start with the first folder; All Chats is shown only while the tabs are reordered. */
    public static final NeoConfigItem.BoolItem HIDE_ALL_CHATS_TAB = NeoConfig.bool("hide_all_chats_tab", false);

    // ---- Chats: message menu and double tap ---------------------------------------------------------

    /** "Repeat" (+1): forward the message into the same chat. */
    public static final NeoConfigItem.BoolItem MENU_REPEAT = NeoConfig.bool("menu_repeat", true);
    /** "Save Message": forward to Saved Messages in one tap. */
    public static final NeoConfigItem.BoolItem MENU_SAVE = NeoConfig.bool("menu_save", true);
    public static final NeoConfigItem.BoolItem MENU_COPY_PHOTO = NeoConfig.bool("menu_copy_photo", false);
    public static final NeoConfigItem.BoolItem MENU_DELETE_FILE = NeoConfig.bool("menu_delete_file", false);
    public static final NeoConfigItem.BoolItem MENU_STATISTICS = NeoConfig.bool("menu_statistics", false);
    public static final NeoConfigItem.BoolItem MENU_DETAILS = NeoConfig.bool("menu_details", false);
    /** "Set a reminder": a scheduled copy in Saved Messages. */
    public static final NeoConfigItem.BoolItem MENU_REMINDER = NeoConfig.bool("menu_reminder", false);
    /** "Open in…" for downloaded videos. */
    public static final NeoConfigItem.BoolItem MENU_OPEN_IN = NeoConfig.bool("menu_open_in", false);
    /** "QR Code": read a QR code from a photo. */
    public static final NeoConfigItem.BoolItem MENU_QR = NeoConfig.bool("menu_qr", false);
    /** "Save to gallery" for static stickers, as PNG. */
    public static final NeoConfigItem.BoolItem MENU_SAVE_STICKER = NeoConfig.bool("menu_save_sticker", false);
    /** The stock "Report" item; off hides it. */
    public static final NeoConfigItem.BoolItem MENU_REPORT = NeoConfig.bool("menu_report", true);
    /** Double tap on incoming / outgoing messages; REACTION is the stock behaviour. */
    public static final NeoConfigItem.EnumItem<ru.llacker.neogram.chat.NeoDoubleTap.Action> DOUBLE_TAP_IN =
            NeoConfig.choice("double_tap_in", ru.llacker.neogram.chat.NeoDoubleTap.Action.REACTION);
    public static final NeoConfigItem.EnumItem<ru.llacker.neogram.chat.NeoDoubleTap.Action> DOUBLE_TAP_OUT =
            NeoConfig.choice("double_tap_out", ru.llacker.neogram.chat.NeoDoubleTap.Action.REACTION);

    // ---- Chats: chat screen ------------------------------------------------------------------------

    /** No pull at the end of a channel or forum topic to the next unread one. */
    public static final NeoConfigItem.BoolItem DISABLE_NEXT_CHANNEL = NeoConfig.bool("disable_next_channel", false);
    /** No panel above chats that a business bot manages. */
    public static final NeoConfigItem.BoolItem HIDE_BUSINESS_BOT_PANEL = NeoConfig.bool("hide_business_bot_panel", false);
    /** Size of stickers in chats, percent of the stock size. */
    public static final NeoConfigItem.IntItem STICKER_SIZE = NeoConfig.integer("sticker_size", 100);
    /** No time pill on stickers and large emoji (still shown while the message is selected). */
    public static final NeoConfigItem.BoolItem HIDE_TIME_ON_STICKERS = NeoConfig.bool("hide_time_on_stickers", false);
    /** Dragging the message list closes the keyboard or the emoji panel. */
    public static final NeoConfigItem.BoolItem HIDE_KEYBOARD_ON_SCROLL = NeoConfig.bool("hide_keyboard_on_scroll", false);
    /** "Delete for everyone" starts ticked in the delete messages dialog. */
    public static final NeoConfigItem.BoolItem DELETE_FOR_ALL_DEFAULT = NeoConfig.bool("delete_for_all_default", false);
    /** Every link first tries to open in Instant View, not only telegra.ph. */
    public static final NeoConfigItem.BoolItem LINKS_IN_INSTANT_VIEW = NeoConfig.bool("links_in_instant_view", false);
    /** Empty chats do not offer a greeting sticker. */
    public static final NeoConfigItem.BoolItem HIDE_GREETING_STICKER = NeoConfig.bool("hide_greeting_sticker", false);

    // ---- Privacy -----------------------------------------------------------------------------------

    /** Confirm before starting a voice or video call (as in lawXgram, on by default). */
    public static final NeoConfigItem.BoolItem ASK_BEFORE_CALL = NeoConfig.bool("ask_before_call", true);
    /** Private chats with people who are not contacts notify without sound. */
    public static final NeoConfigItem.BoolItem SILENCE_NON_CONTACTS = NeoConfig.bool("silence_non_contacts", false);
    /** Message notifications take the accent colour of the current theme instead of the stock blue. */
    public static final NeoConfigItem.BoolItem ACCENT_NOTIFICATION_COLOR = NeoConfig.bool("accent_notification_color", false);

    // ---- Media -------------------------------------------------------------------------------------

    /** The live camera tile at the start of the attachment gallery (stock). */
    public static final NeoConfigItem.BoolItem GALLERY_CAMERA = NeoConfig.bool("gallery_camera", true);
    /** Playback stops after the tapped voice or video message instead of going on to the next one. */
    public static final NeoConfigItem.BoolItem DISABLE_VOICE_AUTOPLAY = NeoConfig.bool("disable_voice_autoplay", false);
    /** Videos open in their original quality unless another one was picked for them. */
    public static final NeoConfigItem.BoolItem PREFER_ORIGINAL_QUALITY = NeoConfig.bool("prefer_original_quality", false);
    /** A volume key in a chat plays the visible video with sound (stock). */
    public static final NeoConfigItem.BoolItem VOLUME_BUTTONS_PLAY_VIDEOS = NeoConfig.bool("volume_buttons_play_videos", true);

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

    // ---- Advanced ----------------------------------------------------------------------------------

    /** Connect to Telegram over IPv6 only when the device has an IPv6 address. */
    public static final NeoConfigItem.BoolItem PREFER_IPV6 = NeoConfig.bool("prefer_ipv6", false);

    // ---- About / updates ----------------------------------------------------------------------

    /** Check GitHub releases for a newer direct build on start (at most once per interval). */
    public static final NeoConfigItem.BoolItem AUTO_UPDATE_CHECK = NeoConfig.bool("auto_update_check", true);
    /** Last successful automatic update check, epoch millis; device-specific. */
    public static final NeoConfigItem.LongItem LAST_UPDATE_CHECK = NeoConfig.longValue("last_update_check", 0L).notExportable();
    /** Release tag the user chose to skip. */
    public static final NeoConfigItem.StringItem SKIPPED_UPDATE = NeoConfig.string("skipped_update", "").notExportable();
}
