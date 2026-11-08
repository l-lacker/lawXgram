package ru.llacker.neogram.media;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Media options: patches debloat/gallery-camera, debloat/voice-autoplay,
 * debloat/volume-buttons-video and feature/original-video-quality.
 */
public final class NeoMedia {

    private NeoMedia() {
    }

    public static boolean hideGalleryCamera() {
        return !NeoOptions.GALLERY_CAMERA.value();
    }

    public static boolean disableVoiceAutoplay() {
        return NeoOptions.DISABLE_VOICE_AUTOPLAY.value();
    }

    /** A volume key in a chat plays the visible video with sound (stock). */
    public static boolean volumeButtonsPlayVideos() {
        return NeoOptions.VOLUME_BUTTONS_PLAY_VIDEOS.value();
    }

    /**
     * Quality a video opens with when the user has not picked one for it: its original file with
     * the option on, otherwise none, which leaves the stock adaptive choice.
     */
    @Nullable
    public static VideoPlayer.Quality defaultQuality(@Nullable ArrayList<VideoPlayer.Quality> qualities) {
        if (qualities == null || !NeoOptions.PREFER_ORIGINAL_QUALITY.value()) {
            return null;
        }
        for (VideoPlayer.Quality quality : qualities) {
            if (quality.original) {
                return quality;
            }
        }
        return null;
    }
}
