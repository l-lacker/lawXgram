package ru.llacker.neogram.settings.sections;

import androidx.annotation.NonNull;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.utils.recorder.audio.AudioRecordingSession;
import org.telegram.utils.settings.SharedSettings;

import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.config.NeoValue;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/** Settings section "media": video and voice messages, attachments and playback. */
public final class MediaSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("media", R.string.NeoSectionMedia,
            R.string.NeoSectionMediaInfo, R.drawable.neo_section_media, IconBackgroundColors.RED, MediaSection::rows);

    private MediaSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.page("round_video", RoundVideoSection.SECTION, R.string.NeoRoundVideoInfo));

        rows.add(NeoSettingsRow.header(R.string.AudioRecorderSettings));
        rows.add(NeoSettingsRow.toggle("audio_recorder_v2", NeoValue.of(
                () -> MediaController.shouldUseAudioRecorderV2(UserConfig.selectedAccount),
                SharedSettings.audioRecorderV2Enabled::set), R.string.AudioRecorderUseNewRecorder, R.string.NeoVoiceRecorderInfo));
        rows.add(NeoSettingsRow.choice("audio_noise_suppression", NeoValue.of(SharedSettings.audioRecorderNoiseSuppression::get,
                        SharedSettings.audioRecorderNoiseSuppression::set), R.string.AudioRecorderNoiseSuppression, R.string.NeoNoiseSuppressionInfo)
                .option(AudioRecordingSession.NoiseSuppression.DISABLED, R.string.AudioRecorderNoiseSuppressionOff)
                .option(AudioRecordingSession.NoiseSuppression.RNNOISE, R.string.AudioRecorderNoiseSuppressionRNNoise)
                .build()
                .onlyIf(() -> MediaController.shouldUseAudioRecorderV2(UserConfig.selectedAccount)));

        rows.add(NeoSettingsRow.header(R.string.NeoAttachHeader));
        rows.add(NeoSettingsRow.toggle("gallery_camera", NeoOptions.GALLERY_CAMERA,
                R.string.NeoShowGalleryCamera, R.string.NeoShowGalleryCameraInfo));

        rows.add(NeoSettingsRow.header(R.string.NeoPlaybackHeader));
        rows.add(NeoSettingsRow.toggle("disable_voice_autoplay", NeoOptions.DISABLE_VOICE_AUTOPLAY,
                R.string.NeoDisableVoiceMessagesAutoPlay, R.string.NeoDisableVoiceMessagesAutoPlayInfo));
        rows.add(NeoSettingsRow.toggle("prefer_original_quality", NeoOptions.PREFER_ORIGINAL_QUALITY,
                R.string.NeoPreferOriginalQuality, R.string.NeoPreferOriginalQualityInfo));
        rows.add(NeoSettingsRow.toggle("volume_buttons_play_videos", NeoOptions.VOLUME_BUTTONS_PLAY_VIDEOS,
                R.string.NeoUnmuteVideosWithVolumeButtons, R.string.NeoUnmuteVideosWithVolumeButtonsInfo));
    }
}
