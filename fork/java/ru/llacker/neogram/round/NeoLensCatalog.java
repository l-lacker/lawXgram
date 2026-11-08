package ru.llacker.neogram.round;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Range;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Lenses of the back camera that video messages can switch between without reopening the camera.
 *
 * <p>Since Android 11 a logical multi-camera exposes its ultra-wide and telephoto modules through
 * {@code CONTROL_ZOOM_RATIO}: a ratio below 1 is the ultra-wide, the HAL switches the physical
 * sensor itself, even while recording. This is the path Google recommends and the one that works
 * on devices whose vendor hides auxiliary cameras from third-party apps. Opening physical cameras
 * directly ({@code setPhysicalCameraId}) is deliberately not used: stream combinations differ per
 * device and some devices crash on it.</p>
 *
 * <p>Zoom ratios of the modules are estimated from focal length relative to sensor width (the
 * field of view), not from the focal length alone, which is meaningless across sensor sizes.</p>
 */
public final class NeoLensCatalog {

    public static final class Lens {
        public final NeoOptions.RoundLens kind;
        public final float zoomRatio;

        Lens(NeoOptions.RoundLens kind, float zoomRatio) {
            this.kind = kind;
            this.zoomRatio = zoomRatio;
        }

        /** "0.5", "1", "3" — shown on the lens buttons. */
        public String label() {
            float rounded = Math.round(zoomRatio * 10f) / 10f;
            return rounded == Math.round(rounded) ? String.valueOf(Math.round(rounded)) : String.valueOf(rounded);
        }
    }

    /** Tolerance below 1× for "this camera has an ultra-wide". */
    private static final float WIDE_THRESHOLD = 0.95f;
    /** A module at least this much narrower than the main one counts as telephoto. */
    private static final float TELE_THRESHOLD = 1.8f;
    /** Digital/hybrid "2×" when no telephoto module is visible but the zoom range allows it. */
    private static final float DIGITAL_TELE = 2f;

    private static volatile List<Lens> backLenses;

    private NeoLensCatalog() {
    }

    /** Lenses of the back camera in ascending zoom order; empty when there is nothing to switch. */
    @NonNull
    public static List<Lens> backLenses() {
        List<Lens> cached = backLenses;
        if (cached == null) {
            cached = backLenses = Collections.unmodifiableList(compute());
        }
        return cached;
    }

    @Nullable
    public static Lens find(@NonNull NeoOptions.RoundLens kind) {
        for (Lens lens : backLenses()) {
            if (lens.kind == kind) {
                return lens;
            }
        }
        return null;
    }

    private static List<Lens> compute() {
        List<Lens> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return result;
        }
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics back = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics candidate = manager.getCameraCharacteristics(id);
                Integer facing = candidate.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                if (back == null || NeoRoundCamera.compareBackCameras(candidate, back) > 0) {
                    back = candidate;
                }
            }
            if (back == null) {
                return result;
            }
            Range<Float> range = back.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range == null) {
                return result;
            }
            float lower = range.getLower();
            float upper = range.getUpper();
            float tele = 0f;
            float wide = lower < WIDE_THRESHOLD ? lower : 0f;
            float mainFov = normalizedFocal(back);
            if (mainFov > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Set<String> physicalIds = back.getPhysicalCameraIds();
                for (String physicalId : physicalIds) {
                    float ratio;
                    try {
                        ratio = normalizedFocal(manager.getCameraCharacteristics(physicalId)) / mainFov;
                    } catch (Exception e) {
                        continue; // hidden or virtual module
                    }
                    if (ratio >= TELE_THRESHOLD && ratio <= upper && (tele == 0f || ratio < tele)) {
                        tele = ratio;
                    }
                }
            }
            if (tele == 0f && upper >= DIGITAL_TELE) {
                tele = DIGITAL_TELE;
            }
            if (wide > 0f) {
                result.add(new Lens(NeoOptions.RoundLens.WIDE, wide));
            }
            if (wide > 0f || tele > 0f) {
                result.add(new Lens(NeoOptions.RoundLens.MAIN, 1f));
            }
            if (tele > 0f && (wide > 0f || tele > DIGITAL_TELE)) {
                result.add(new Lens(NeoOptions.RoundLens.TELE, tele));
            }
            if (result.size() < 2) {
                result.clear();
            }
        } catch (Throwable e) {
            FileLog.e(e);
            result.clear();
        }
        return result;
    }

    /** Focal length relative to sensor width: proportional to 1 / field of view. 0 if unknown. */
    static float normalizedFocal(@NonNull CameraCharacteristics characteristics) {
        float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focals == null || focals.length == 0 || sensor == null || sensor.getWidth() <= 0f) {
            return 0f;
        }
        return focals[0] / sensor.getWidth();
    }
}
