package ru.llacker.lawxgram.helpers;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VideoEditedInfo;

import java.util.Locale;

import ru.llacker.lawxgram.LawxConfig;

public final class RoundVideoQualityHelper {

    public static final int TELEGRAM_MAX_SIDE = 640;
    public static final int TELEGRAM_MAX_FPS = 60;
    public static final int DEFAULT_FPS_CAP = 30;
    public static final int MIN_BITRATE_MBPS = 1;
    public static final int STANDARD_BITRATE_MBPS = 1;
    public static final int MEDIUM_BITRATE_MBPS = 3;
    public static final int HIGH_BITRATE_MBPS = 5;
    public static final int MAX_BITRATE_MBPS = HIGH_BITRATE_MBPS;
    public static final long TELEGRAM_MAX_SIZE_BYTES = 10L * 1024L * 1024L;
    public static final long MAX_RECORDING_DURATION_MS = 60_000L;

    private static final float MUX_OVERHEAD = 1.05f;
    private static final int SIDE_STEP = 16;

    private RoundVideoQualityHelper() {
    }

    public static int getTelegramStandardSide(int account) {
        account = validAccount(account);
        return encoderSafeSide(Math.max(SIDE_STEP, MessagesController.getInstance(account).roundVideoSize));
    }

    public static int clampConfiguredSide(int account, int side) {
        int min = getTelegramStandardSide(account);
        int result = Math.max(min, Math.min(TELEGRAM_MAX_SIDE, side));
        return encoderSafeSide(result);
    }

    public static int getConfiguredSide(int account) {
        return clampConfiguredSide(account, LawxConfig.roundVideoResolution);
    }

    public static int getConfiguredFpsCap() {
        return LawxConfig.roundVideoFpsCap >= TELEGRAM_MAX_FPS ? TELEGRAM_MAX_FPS : DEFAULT_FPS_CAP;
    }

    public static int getConfiguredBitrateCap() {
        return clampBitrateMbps(LawxConfig.roundVideoBitrateMbps) * 1_000_000;
    }

    public static int clampBitrateMbps(int value) {
        if (value <= STANDARD_BITRATE_MBPS) {
            return STANDARD_BITRATE_MBPS;
        } else if (value <= MEDIUM_BITRATE_MBPS) {
            return MEDIUM_BITRATE_MBPS;
        }
        return HIGH_BITRATE_MBPS;
    }

    public static int presetIndexForBitrate(int value) {
        value = clampBitrateMbps(value);
        if (value == STANDARD_BITRATE_MBPS) {
            return 0;
        } else if (value == MEDIUM_BITRATE_MBPS) {
            return 1;
        }
        return 2;
    }

    public static int bitrateForPresetIndex(int index) {
        if (index <= 0) {
            return STANDARD_BITRATE_MBPS;
        } else if (index == 1) {
            return MEDIUM_BITRATE_MBPS;
        }
        return HIGH_BITRATE_MBPS;
    }

    public static int encoderSafeSide(int side) {
        side = Math.max(2, Math.min(TELEGRAM_MAX_SIDE, side));
        if (side > SIDE_STEP) {
            side -= side % SIDE_STEP;
        }
        if ((side & 1) != 0) {
            side--;
        }
        return Math.max(2, side);
    }

    public static int chooseOutputSide(int account, int transformedWidth, int transformedHeight) {
        return getConfiguredSide(account);
    }

    public static int chooseFps(int sourceFps) {
        int cap = getConfiguredFpsCap();
        if (sourceFps <= 0) {
            return Math.min(DEFAULT_FPS_CAP, cap);
        }
        return Math.max(1, Math.min(sourceFps, cap));
    }

    public static int chooseCameraFps(int supportedFps) {
        int cap = getConfiguredFpsCap();
        if (supportedFps <= 0) {
            return cap;
        }
        return Math.max(1, Math.min(supportedFps, cap));
    }

    public static int calculateTargetVideoBitrate(int account, VideoEditedInfo info, int side) {
        int originalWidth = info != null ? info.originalWidth : side;
        int originalHeight = info != null ? info.originalHeight : side;
        int originalBitrate = info != null && info.originalBitrate > 0 ? info.originalBitrate : info != null ? info.bitrate : 0;
        long duration = getDurationMs(info);
        return calculateTargetVideoBitrate(account, originalWidth, originalHeight, originalBitrate, side, duration);
    }

    public static int calculateTargetVideoBitrate(int account, int originalWidth, int originalHeight, int originalBitrate, int side, long durationMs) {
        int userCap = getConfiguredBitrateCap();
        int sourceScaledBitrate = calculateSourceScaledBitrate(originalBitrate, userCap);
        int durationCap = calculateDurationBitrateCap(account, durationMs);
        int bitrate = Math.min(userCap, Math.min(sourceScaledBitrate, durationCap));
        return Math.max(64_000, bitrate);
    }

    public static int calculateRecordingBitrate(int account, int side, int fps) {
        int bitrate = getConfiguredBitrateCap();
        return calculateTargetVideoBitrate(account, side, side, bitrate, side, 0);
    }

    public static long estimateRoundVideoSize(int account, int videoBitrate, long durationMs) {
        if (durationMs <= 0) {
            return 1;
        }
        int audioBitrate = getAudioBitrate(account);
        double seconds = durationMs / 1000.0;
        long estimated = (long) Math.ceil((videoBitrate + audioBitrate) * seconds / 8.0 * MUX_OVERHEAD);
        return Math.max(1, Math.min(TELEGRAM_MAX_SIZE_BYTES, estimated));
    }

    public static void applyQuality(VideoEditedInfo info, int account, int side) {
        if (info == null || !info.roundVideo) {
            return;
        }
        int outputSide = encoderSafeSide(side);
        if (info.cropState != null) {
            info.cropState.transformWidth = outputSide;
            info.cropState.transformHeight = outputSide;
        }
        info.resultWidth = outputSide;
        info.resultHeight = outputSide;
        info.framerate = chooseFps(info.framerate);
        info.bitrate = calculateTargetVideoBitrate(account, info, outputSide);
        info.estimatedSize = estimateRoundVideoSize(account, info.bitrate, getDurationMs(info));
    }

    public static void applyRecordingMetadata(VideoEditedInfo info, int account, int side, int fps, int sourceBitrate, long durationMs, long actualSize) {
        if (info == null) {
            return;
        }
        side = encoderSafeSide(side);
        info.roundVideo = true;
        info.fromCamera = true;
        info.framerate = chooseFps(fps);
        info.originalWidth = side;
        info.originalHeight = side;
        info.resultWidth = side;
        info.resultHeight = side;
        info.originalBitrate = sourceBitrate;
        info.originalDuration = durationMs > 0 ? durationMs * 1000L : 0;
        info.estimatedDuration = durationMs;
        info.bitrate = calculateTargetVideoBitrate(account, side, side, sourceBitrate, side, durationMs);
        info.estimatedSize = durationMs > 0 ? estimateRoundVideoSize(account, info.bitrate, durationMs) : Math.max(1, Math.min(TELEGRAM_MAX_SIZE_BYTES, actualSize));
    }

    public static long getDurationMs(VideoEditedInfo info) {
        if (info == null) {
            return 0;
        }
        if (info.estimatedDuration > 0) {
            return info.estimatedDuration;
        }
        if (info.startTime >= 0 && info.endTime > info.startTime) {
            return normalizeDurationMs(info.endTime - info.startTime);
        }
        if (info.originalDuration > 0) {
            return normalizeDurationMs(info.originalDuration);
        }
        return 0;
    }

    public static String formatSizeMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static int calculateSourceScaledBitrate(int originalBitrate, int userCap) {
        if (originalBitrate <= 0) {
            return userCap;
        }
        return Math.max(64_000, Math.min(originalBitrate, userCap));
    }

    private static int calculateDurationBitrateCap(int account, long durationMs) {
        if (durationMs <= 0) {
            return getConfiguredBitrateCap();
        }
        double seconds = Math.max(0.1, durationMs / 1000.0);
        double totalBitsBudget = TELEGRAM_MAX_SIZE_BYTES * 8.0 / MUX_OVERHEAD;
        double audioBits = getAudioBitrate(account) * seconds;
        return Math.max(64_000, (int) Math.floor((totalBitsBudget - audioBits) / seconds));
    }

    private static long normalizeDurationMs(long duration) {
        if (duration > MAX_RECORDING_DURATION_MS * 10L) {
            return Math.max(1, duration / 1000L);
        }
        return duration;
    }

    private static int getAudioBitrate(int account) {
        account = validAccount(account);
        return Math.max(0, MessagesController.getInstance(account).roundAudioBitrate) * 1024;
    }

    private static int validAccount(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return UserConfig.selectedAccount;
        }
        return account;
    }
}
