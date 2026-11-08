package ru.llacker.neogram.core.round;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class LensMathTest {

    private static String describe(List<LensMath.Lens> lenses) {
        return lenses.toString();
    }

    @Test
    public void ultraWideAndTelephotoModules() {
        // Pixel-like: 0.5–30 range, a 4.8x telephoto among the physical cameras, the ultra-wide too
        List<LensMath.Lens> lenses = LensMath.lenses(0.5f, 30f, 1f, new float[]{1f, 0.5f, 4.8f});
        assertEquals("[WIDE=0.5, MAIN=1.0, TELE=4.8]", describe(lenses));
    }

    @Test
    public void nearestTelephotoWins() {
        List<LensMath.Lens> lenses = LensMath.lenses(0.6f, 100f, 1f, new float[]{10f, 3f});
        assertEquals("[WIDE=0.6, MAIN=1.0, TELE=3.0]", describe(lenses));
    }

    @Test
    public void telephotoOutsideTheZoomRangeIsIgnored() {
        List<LensMath.Lens> lenses = LensMath.lenses(0.6f, 2.5f, 1f, new float[]{3f});
        assertEquals("[WIDE=0.6, MAIN=1.0, TELE=2.0]", describe(lenses));
    }

    @Test
    public void digitalTwoTimesOnlyNextToAnUltraWide() {
        assertEquals("[WIDE=0.6, MAIN=1.0, TELE=2.0]", describe(LensMath.lenses(0.6f, 8f, 1f, new float[0])));
        // no ultra-wide and no telephoto module: pinch zoom covers digital zoom, nothing to switch
        assertTrue(LensMath.lenses(1f, 8f, 1f, new float[0]).isEmpty());
    }

    @Test
    public void telephotoWithoutUltraWide() {
        List<LensMath.Lens> lenses = LensMath.lenses(1f, 10f, 1f, new float[]{2.9f});
        assertEquals("[MAIN=1.0, TELE=2.9]", describe(lenses));
    }

    @Test
    public void unknownFocalsKeepTheZoomRange() {
        List<LensMath.Lens> lenses = LensMath.lenses(0.55f, 1.5f, 0f, new float[]{4f});
        assertEquals("[WIDE=0.55, MAIN=1.0]", describe(lenses));
    }

    @Test
    public void lowerBoundJustBelowOneIsNotAnUltraWide() {
        assertTrue(LensMath.lenses(0.97f, 1f, 1f, new float[0]).isEmpty());
    }

    @Test
    public void brokenRangesGiveNoLenses() {
        assertTrue(LensMath.lenses(0f, 10f, 1f, new float[0]).isEmpty());
        assertTrue(LensMath.lenses(2f, 1f, 1f, new float[0]).isEmpty());
        assertTrue(LensMath.lenses(Float.NaN, 10f, 1f, new float[0]).isEmpty());
    }

    @Test
    public void labels() {
        assertEquals("0.6", LensMath.label(0.6f));
        assertEquals("0.5", LensMath.label(0.5f));
        assertEquals("0.6", LensMath.label(0.55f));
        assertEquals("1×", LensMath.label(1f));
        assertEquals("1×", LensMath.label(0.98f));
        assertEquals("2×", LensMath.label(2f));
        assertEquals("4.8", LensMath.label(4.8f));
    }
}
