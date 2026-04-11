package ru.llacker.lawxgram.helpers;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import ru.llacker.lawxgram.LawxConfig;

public class VoiceEnhancementsHelper {
    private static AutomaticGainControl automaticGainControl;
    private static NoiseSuppressor noiseSuppressor;
    private static AcousticEchoCanceler acousticEchoCanceler;

    public static void initVoiceEnhancements(int audioSessionId) {
        if (!LawxConfig.voiceEnhancements) return;

        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(audioSessionId);
            automaticGainControl.setEnabled(true);
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId);
            noiseSuppressor.setEnabled(true);
        }
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId);
            acousticEchoCanceler.setEnabled(true);
        }
    }

    public static void releaseVoiceEnhancements() {
        if (!LawxConfig.voiceEnhancements) return;

        if (automaticGainControl != null) {
            automaticGainControl.release();
            automaticGainControl = null;
        }
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
        if (acousticEchoCanceler != null) {
            acousticEchoCanceler.release();
            acousticEchoCanceler = null;
        }
    }

    public static boolean isAvailable() {
        return AutomaticGainControl.isAvailable() || NoiseSuppressor.isAvailable() || AcousticEchoCanceler.isAvailable();
    }
}
