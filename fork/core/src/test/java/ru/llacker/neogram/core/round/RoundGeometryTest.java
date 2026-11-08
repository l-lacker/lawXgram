package ru.llacker.neogram.core.round;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RoundGeometryTest {

    private static final float EPS = 1e-5f;

    @Test
    public void landscapeKeepsTheCentralSquare() {
        RoundGeometry.Square square = RoundGeometry.square(1920, 1080, 0, 1f, 1f, 1f, 480);
        assertEquals(1080f / 1920f, square.cropPw, EPS);
        assertEquals(1f, square.cropPh, EPS);
        assertEquals(480, square.side);
    }

    @Test
    public void portraitRecordedRotated() {
        // a phone portrait video: 1920x1080 stored with rotation 90, shown 1080 wide and 1920 tall
        RoundGeometry.Square square = RoundGeometry.square(1920, 1080, 90, 1f, 1f, 1f, 624);
        assertEquals(1f, square.cropPw, EPS);
        assertEquals(1080f / 1920f, square.cropPh, EPS);
        assertEquals(624, square.side);
    }

    @Test
    public void userCropIsNarrowedToItsSquare() {
        // the user kept the left half of a 1000x1000 frame: a 500x1000 window becomes 500x500
        RoundGeometry.Square square = RoundGeometry.square(1000, 1000, 0, 0.5f, 1f, 1f, 480);
        assertEquals(0.5f, square.cropPw, EPS);
        assertEquals(0.5f, square.cropPh, EPS);
        assertEquals(480, square.side);
    }

    @Test
    public void alreadySquare() {
        RoundGeometry.Square square = RoundGeometry.square(720, 720, 0, 1f, 1f, 1f, 480);
        assertEquals(1f, square.cropPw, EPS);
        assertEquals(1f, square.cropPh, EPS);
        assertEquals(480, square.side);
    }

    @Test
    public void smallSourcesAreNotScaledUpBeyondTheirDetail() {
        // a 400x300 clip: the square has 300 source pixels, so 288 (16-aligned), not 480
        assertEquals(288, RoundGeometry.square(400, 300, 0, 1f, 1f, 1f, 480).side);
        // measured in source pixels when the frame given is a downscaled one
        assertEquals(480, RoundGeometry.square(400, 300, 0, 1f, 1f, 2f, 480).side);
    }

    @Test
    public void outputSides() {
        assertEquals(352, RoundGeometry.outputSide(1080, 360)); // 360 is not 16-aligned
        assertEquals(624, RoundGeometry.outputSide(1080, 624));
        assertEquals(624, RoundGeometry.outputSide(1080, 700)); // stays below the 640 limit
        assertEquals(240, RoundGeometry.outputSide(100, 480)); // tiny sources are scaled up to the minimum
        assertEquals(240, RoundGeometry.outputSide(1080, 0));
    }

    @Test
    public void brokenCropFractionsMeanNoCrop() {
        RoundGeometry.Square square = RoundGeometry.square(1280, 720, 0, 0f, 2f, 1f, 480);
        assertEquals(720f / 1280f, square.cropPw, EPS);
        assertEquals(1f, square.cropPh, EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyFrameIsRejected() {
        RoundGeometry.square(0, 720, 0, 1f, 1f, 1f, 480);
    }

    @Test
    public void trimKeepsShortRanges() {
        assertArrayEquals(new long[]{0L, 30_000_000L}, RoundGeometry.trim(-1L, -1L, 30_000_000L));
        assertArrayEquals(new long[]{5_000_000L, 20_000_000L}, RoundGeometry.trim(5_000_000L, 20_000_000L, 30_000_000L));
    }

    @Test
    public void trimCutsToOneMinuteFromTheStart() {
        assertArrayEquals(new long[]{0L, 60_000_000L}, RoundGeometry.trim(-1L, -1L, 90_000_000L));
        assertArrayEquals(new long[]{10_000_000L, 70_000_000L}, RoundGeometry.trim(10_000_000L, 85_000_000L, 90_000_000L));
    }

    @Test
    public void trimRepairsBrokenRanges() {
        // end past the duration, end before start
        assertArrayEquals(new long[]{0L, 30_000_000L}, RoundGeometry.trim(0L, 45_000_000L, 30_000_000L));
        assertArrayEquals(new long[]{20_000_000L, 30_000_000L}, RoundGeometry.trim(20_000_000L, 10_000_000L, 30_000_000L));
    }
}
