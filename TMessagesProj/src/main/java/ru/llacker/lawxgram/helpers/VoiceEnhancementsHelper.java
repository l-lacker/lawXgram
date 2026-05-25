package ru.llacker.lawxgram.helpers;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import org.telegram.messenger.FileLog;

import ru.llacker.lawxgram.LawxConfig;

public class VoiceEnhancementsHelper {
    private static AutomaticGainControl automaticGainControl;
    private static NoiseSuppressor noiseSuppressor;
    private static AcousticEchoCanceler acousticEchoCanceler;

    public static void initVoiceEnhancements(int audioSessionId) {
        releaseVoiceEnhancements();
        if (!LawxConfig.voiceEnhancements) {
            return;
        }

        if (AutomaticGainControl.isAvailable()) {
            try {
                AutomaticGainControl effect = AutomaticGainControl.create(audioSessionId);
                if (enableEffect(effect)) {
                    automaticGainControl = effect;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            try {
                NoiseSuppressor effect = NoiseSuppressor.create(audioSessionId);
                if (enableEffect(effect)) {
                    noiseSuppressor = effect;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                AcousticEchoCanceler effect = AcousticEchoCanceler.create(audioSessionId);
                if (enableEffect(effect)) {
                    acousticEchoCanceler = effect;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static void releaseVoiceEnhancements() {
        if (automaticGainControl != null) {
            releaseEffect(automaticGainControl);
            automaticGainControl = null;
        }
        if (noiseSuppressor != null) {
            releaseEffect(noiseSuppressor);
            noiseSuppressor = null;
        }
        if (acousticEchoCanceler != null) {
            releaseEffect(acousticEchoCanceler);
            acousticEchoCanceler = null;
        }
    }

    private static boolean enableEffect(AudioEffect effect) {
        if (effect == null) {
            return false;
        }
        try {
            effect.setEnabled(true);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            releaseEffect(effect);
            return false;
        }
    }

    private static void releaseEffect(AudioEffect effect) {
        try {
            effect.release();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isAvailable() {
        return AutomaticGainControl.isAvailable() || NoiseSuppressor.isAvailable() || AcousticEchoCanceler.isAvailable();
    }
}
