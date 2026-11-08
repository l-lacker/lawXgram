package ru.llacker.neogram.core.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which lenses of a logical back camera video messages offer, from what Camera2 reports about it.
 *
 * <p>The zoom ratio range of a logical multi-camera ({@code CONTROL_ZOOM_RATIO_RANGE}) tells how
 * wide it can go: a lower bound below 1 is its ultra-wide module. Telephoto modules are found among
 * its physical cameras by field of view relative to the main one: focal length divided by sensor
 * width ("normalized focal"), since focal length alone means nothing across sensor sizes.</p>
 */
public final class LensMath {

    public enum Kind { WIDE, MAIN, TELE }

    public static final class Lens {
        public final Kind kind;
        public final float zoomRatio;

        public Lens(Kind kind, float zoomRatio) {
            this.kind = kind;
            this.zoomRatio = zoomRatio;
        }

        /** "0.6", "1×", "3×": the text of the lens button. */
        public String label() {
            return LensMath.label(zoomRatio);
        }

        @Override
        public String toString() {
            return kind + "=" + zoomRatio;
        }
    }

    /** Tolerance below 1× for "this camera reaches an ultra-wide". */
    static final float WIDE_THRESHOLD = 0.95f;
    /** A module at least this much narrower than the main one counts as telephoto. */
    static final float TELE_THRESHOLD = 1.8f;
    /** Digital "2×" offered next to an ultra-wide when the camera has no telephoto module. */
    static final float DIGITAL_TELE = 2f;

    private LensMath() {
    }

    /**
     * Lenses in ascending zoom order: at least two, or none when there is nothing to switch.
     *
     * @param lowerZoom      lower bound of the zoom ratio range
     * @param upperZoom      upper bound of the zoom ratio range
     * @param mainFocal      normalized focal of the logical camera, 0 if unknown
     * @param physicalFocals normalized focals of its physical cameras, 0 for unknown ones
     */
    public static List<Lens> lenses(float lowerZoom, float upperZoom, float mainFocal, float[] physicalFocals) {
        if (!(lowerZoom > 0f) || upperZoom < lowerZoom) {
            return Collections.emptyList();
        }
        float wide = lowerZoom < WIDE_THRESHOLD ? lowerZoom : 0f;
        float tele = 0f;
        if (mainFocal > 0f && physicalFocals != null) {
            for (float focal : physicalFocals) {
                if (!(focal > 0f)) {
                    continue;
                }
                float ratio = focal / mainFocal;
                if (ratio >= TELE_THRESHOLD && ratio <= upperZoom && (tele == 0f || ratio < tele)) {
                    tele = ratio;
                }
            }
        }
        if (tele == 0f && wide > 0f && upperZoom >= DIGITAL_TELE) {
            tele = DIGITAL_TELE;
        }
        if (wide == 0f && tele == 0f) {
            return Collections.emptyList();
        }
        List<Lens> result = new ArrayList<>(3);
        if (wide > 0f) {
            result.add(new Lens(Kind.WIDE, wide));
        }
        result.add(new Lens(Kind.MAIN, 1f));
        if (tele > 0f) {
            result.add(new Lens(Kind.TELE, tele));
        }
        return Collections.unmodifiableList(result);
    }

    /** Rounded to one decimal: whole ratios get a "×" ("1×", "3×"), fractional ones do not ("0.6"). */
    public static String label(float zoomRatio) {
        float rounded = Math.round(zoomRatio * 10f) / 10f;
        if (rounded == Math.round(rounded)) {
            return Math.round(rounded) + "×";
        }
        return String.format(Locale.US, "%.1f", rounded);
    }
}
