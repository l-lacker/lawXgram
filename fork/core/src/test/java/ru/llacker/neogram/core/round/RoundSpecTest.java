package ru.llacker.neogram.core.round;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoundSpecTest {

    @Test
    public void validSides() {
        assertTrue(RoundSpec.isValidSide(480));
        assertTrue(RoundSpec.isValidSide(560));
        assertTrue(RoundSpec.isValidSide(624));
        assertFalse("not 16-aligned", RoundSpec.isValidSide(360));
        assertFalse("at the limit", RoundSpec.isValidSide(640));
        assertFalse(RoundSpec.isValidSide(0));
        assertFalse(RoundSpec.isValidSide(-16));
    }

    @Test
    public void sourceCropSizes() {
        assertEquals(1080, RoundSpec.sourceCropSize(560, true));
        assertEquals(1080, RoundSpec.sourceCropSize(624, true));
        assertEquals(840, RoundSpec.sourceCropSize(560, false));
        assertEquals(936, RoundSpec.sourceCropSize(624, false));
        // the stock table for 480: twice in High, 1.5 times in Medium
        assertEquals(960, RoundSpec.sourceCropSize(480, true));
        assertEquals(720, RoundSpec.sourceCropSize(480, false));
    }

    @Test
    public void estimatedSizeOfAMinute() {
        // 1.2 Mbit/s video + 64 kbit/s audio for 60 s: 9.48 MB plus 2 % container overhead
        assertEquals(9_669_600L, RoundSpec.estimatedBytes(1_200_000, RoundSpec.AUDIO_BITRATE, 60_000L));
        assertEquals(0L, RoundSpec.estimatedBytes(0, RoundSpec.AUDIO_BITRATE, 60_000L));
        assertEquals(0L, RoundSpec.estimatedBytes(1_000_000, RoundSpec.AUDIO_BITRATE, 0L));
    }
}
