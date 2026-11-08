package ru.llacker.neogram.core.round;

/**
 * Limits of Telegram video messages (round videos) and the sizes derived from them, in one place
 * for the recorder settings and for turning gallery videos into video messages.
 */
public final class RoundSpec {

    /** Video messages are squares smaller than this many pixels per side. */
    public static final int SIDE_LIMIT = 640;
    /** Encoders of some chips only accept sizes aligned to this. */
    public static final int ALIGNMENT = 16;
    /** Longest video message. */
    public static final long MAXIMUM_DURATION_MS = 60_000L;
    /** AAC bitrate of the recorder. */
    public static final int AUDIO_BITRATE = 64_000;
    /** Short side of the 1080p streams every camera offers. */
    static final int CAMERA_SHORT_SIDE = 1080;
    /** Container overhead on top of the audio and video bitrates, in percent. */
    static final int CONTAINER_OVERHEAD_PERCENT = 2;

    private RoundSpec() {
    }

    /** A side the recorder and the converter may produce: 16-aligned and below {@link #SIDE_LIMIT}. */
    public static boolean isValidSide(int side) {
        return side > 0 && side < SIDE_LIMIT && side % ALIGNMENT == 0;
    }

    /**
     * Side of the camera square scaled into a 560 or 624 output: twice the output in High, capped
     * at the short side of 1080p streams, and 1.5 times in Medium (even, as GL crops want).
     */
    public static int sourceCropSize(int outputSide, boolean high) {
        return high ? Math.min(outputSide * 2, CAMERA_SHORT_SIDE) : (outputSide * 3 / 2) & ~1;
    }

    /** Expected size of a video message, in bytes. */
    public static long estimatedBytes(int videoBitrate, int audioBitrate, long durationMs) {
        if (videoBitrate <= 0 || durationMs <= 0) {
            return 0L;
        }
        long bits = (long) (videoBitrate + Math.max(0, audioBitrate)) * durationMs / 1000L;
        return bits / 8L * (100 + CONTAINER_OVERHEAD_PERCENT) / 100;
    }
}
