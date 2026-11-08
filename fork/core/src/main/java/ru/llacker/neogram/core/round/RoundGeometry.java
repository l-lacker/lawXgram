package ru.llacker.neogram.core.round;

/**
 * Turning a gallery video into a video message: the square of the picture to keep, the output side
 * and the time range, in the terms of Telegram's VideoEditedInfo and MediaController.CropState.
 *
 * <p>A crop there is a window of {@code cropPw × cropPh} of the frame (fractions of the frame as it
 * is shown, i.e. after a 90/270° rotation width and height swap), centred where the user placed it.
 * Narrowing the window to its central square keeps the user's choice of area, position, rotation
 * and zoom; the converter then scales the square to the output side.</p>
 */
public final class RoundGeometry {

    /** Smallest side a video message is encoded with; tiny sources are scaled up to it. */
    public static final int MINIMUM_SIDE = 240;

    /** The square window and the side it is encoded with. */
    public static final class Square {
        public final float cropPw;
        public final float cropPh;
        public final int side;

        Square(float cropPw, float cropPh, int side) {
            this.cropPw = cropPw;
            this.cropPh = cropPh;
            this.side = side;
        }

        @Override
        public String toString() {
            return cropPw + "x" + cropPh + "@" + side;
        }
    }

    private RoundGeometry() {
    }

    /**
     * @param width         frame width the crop fractions refer to (before rotation)
     * @param height        frame height the crop fractions refer to (before rotation)
     * @param rotation      total rotation in degrees (video rotation plus crop rotation)
     * @param cropPw        width of the user's crop window as a fraction of the shown frame, 1 if none
     * @param cropPh        height of the user's crop window as a fraction of the shown frame, 1 if none
     * @param sourceScale   source pixels per frame pixel (original size / {@code width}), at least 1
     * @param preferredSide the side the user chose for video messages
     */
    public static Square square(int width, int height, int rotation, float cropPw, float cropPh,
                                float sourceScale, int preferredSide) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("frame " + width + "x" + height);
        }
        boolean swapped = Math.floorMod(rotation, 180) == 90;
        float shownWidth = swapped ? height : width;
        float shownHeight = swapped ? width : height;
        float windowWidth = clampFraction(cropPw) * shownWidth;
        float windowHeight = clampFraction(cropPh) * shownHeight;
        float side = Math.min(windowWidth, windowHeight);
        int sourceSide = (int) Math.floor(side * Math.max(1f, sourceScale));
        return new Square(side / shownWidth, side / shownHeight, outputSide(sourceSide, preferredSide));
    }

    /**
     * The encoded side: the preferred one, but never more than the source has (no upscaling of
     * detail that is not there), aligned down to 16 for every encoder, and at least {@link #MINIMUM_SIDE}.
     */
    public static int outputSide(int sourceSide, int preferredSide) {
        int side = Math.min(Math.max(preferredSide, MINIMUM_SIDE), Math.max(sourceSide, MINIMUM_SIDE));
        side -= side % RoundSpec.ALIGNMENT;
        side = Math.min(side, RoundSpec.SIDE_LIMIT - RoundSpec.ALIGNMENT);
        return Math.max(side, MINIMUM_SIDE);
    }

    private static float clampFraction(float value) {
        return value > 0f && value <= 1f ? value : 1f;
    }

    /**
     * The time range to convert, in microseconds, as {@code {startUs, endUs}}: the user's trim
     * ({@code -1} = from the beginning / to the end), cut to {@link RoundSpec#MAXIMUM_DURATION_MS}
     * from its start.
     */
    public static long[] trim(long startUs, long endUs, long durationUs) {
        long start = Math.max(0L, startUs);
        long end = endUs < 0 || durationUs > 0 && endUs > durationUs ? durationUs : endUs;
        if (end <= start) {
            end = durationUs > start ? durationUs : start;
        }
        long maximum = RoundSpec.MAXIMUM_DURATION_MS * 1000L;
        if (end - start > maximum) {
            end = start + maximum;
        }
        return new long[]{start, end};
    }
}
