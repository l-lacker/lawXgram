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
import org.telegram.messenger.Utilities;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import ru.llacker.neogram.core.round.LensMath;

/**
 * Lenses of the back camera that video messages can switch between without reopening the camera.
 *
 * <p>Since Android 11 a logical multi-camera exposes its ultra-wide and telephoto modules through
 * {@code CONTROL_ZOOM_RATIO}: a ratio below 1 is the ultra-wide, and the HAL switches the physical
 * sensor itself, even while recording. This is the path Google recommends, it costs nothing extra
 * (one camera stays open) and it is what vendors that hide auxiliary cameras from third-party apps
 * still allow. Opening physical cameras directly ({@code setPhysicalCameraId}) is deliberately not
 * used: stream combinations differ per device and some devices crash on it. Which lenses to offer
 * is decided by {@link LensMath} from what the camera reports.</p>
 *
 * <p>The catalog is read once per process. The camera service is a binder call per camera, so the
 * UI thread only {@link #peek() peeks}; {@link #warmUp()} loads it in the background and the camera
 * thread or a settings page may {@link #get() load} it.</p>
 */
public final class NeoLensCatalog {

    /** The back camera the lenses belong to. */
    public static final class BackCamera {
        public final String id;
        /** Lenses in ascending zoom order, at least two. */
        public final List<LensMath.Lens> lenses;
        /** Whether the camera advertises an auto-exposure range reaching 60 fps. */
        public final boolean supports60Fps;

        BackCamera(String id, List<LensMath.Lens> lenses, boolean supports60Fps) {
            this.id = id;
            this.lenses = lenses;
            this.supports60Fps = supports60Fps;
        }

        public boolean hasWide() {
            return find(LensMath.Kind.WIDE) != null;
        }

        @Nullable
        public LensMath.Lens find(@NonNull LensMath.Kind kind) {
            for (LensMath.Lens lens : lenses) {
                if (lens.kind == kind) {
                    return lens;
                }
            }
            return null;
        }
    }

    private static final BackCamera NONE = new BackCamera("", Collections.emptyList(), false);

    private static volatile BackCamera backCamera;

    private NeoLensCatalog() {
    }

    /** The catalog if it is already loaded and has lenses, else null. Never blocks. */
    @Nullable
    public static BackCamera peek() {
        BackCamera camera = backCamera;
        return camera == null || camera == NONE ? null : camera;
    }

    public static boolean isLoaded() {
        return backCamera != null;
    }

    /** Loads the catalog on a background thread unless it is loaded. */
    public static void warmUp() {
        if (backCamera == null) {
            Utilities.globalQueue.postRunnable(NeoLensCatalog::get);
        }
    }

    /** Loads the catalog on this thread if needed; null when the back camera has nothing to switch. */
    @Nullable
    public static BackCamera get() {
        BackCamera camera = backCamera;
        if (camera == null) {
            synchronized (NeoLensCatalog.class) {
                camera = backCamera;
                if (camera == null) {
                    camera = backCamera = load();
                }
            }
        }
        return camera == NONE ? null : camera;
    }

    @NonNull
    private static BackCamera load() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return NONE;
        }
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return NONE;
            }
            String backId = null;
            CameraCharacteristics back = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics candidate = manager.getCameraCharacteristics(id);
                Integer facing = candidate.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK || !isBackwardCompatible(candidate)) {
                    continue;
                }
                if (back == null || compareBackCameras(candidate, back) > 0) {
                    back = candidate;
                    backId = id;
                }
            }
            if (back == null) {
                return NONE;
            }
            Range<Float> range = back.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range == null) {
                return NONE;
            }
            List<LensMath.Lens> lenses = LensMath.lenses(range.getLower(), range.getUpper(), normalizedFocal(back),
                    physicalFocals(manager, back));
            FileLog.d("neo camera: back camera " + backId + ", zoom " + range + ", lenses " + lenses);
            return lenses.isEmpty() ? NONE : new BackCamera(backId, lenses, supports60Fps(back));
        } catch (Throwable e) {
            FileLog.e(e);
            return NONE;
        }
    }

    /** Normalized focals of the physical cameras of a logical one; 0 for those apps may not read. */
    @NonNull
    private static float[] physicalFocals(@NonNull CameraManager manager, @NonNull CameraCharacteristics camera) {
        Set<String> ids = camera.getPhysicalCameraIds();
        float[] focals = new float[ids.size()];
        int i = 0;
        for (String id : ids) {
            try {
                focals[i] = normalizedFocal(manager.getCameraCharacteristics(id));
            } catch (Exception e) {
                focals[i] = 0f; // a hidden or virtual module
            }
            i++;
        }
        return focals;
    }

    /**
     * Positive when {@code a} is the better back camera for lenses: the one reaching the widest field
     * of view (the logical camera that includes the ultra-wide), then a logical multi-camera.
     */
    static int compareBackCameras(@NonNull CameraCharacteristics a, @NonNull CameraCharacteristics b) {
        int byRange = Float.compare(minimumZoomRatio(b), minimumZoomRatio(a));
        if (byRange != 0) {
            return byRange;
        }
        return Boolean.compare(hasCapability(a, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA),
                hasCapability(b, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));
    }

    private static float minimumZoomRatio(@NonNull CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) {
                return range.getLower();
            }
        }
        return 1f;
    }

    private static boolean isBackwardCompatible(@NonNull CameraCharacteristics characteristics) {
        return hasCapability(characteristics, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);
    }

    private static boolean hasCapability(@NonNull CameraCharacteristics characteristics, int capability) {
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities != null) {
            for (int value : capabilities) {
                if (value == capability) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean supports60Fps(@NonNull CameraCharacteristics characteristics) {
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges != null) {
            for (Range<Integer> range : ranges) {
                int upper = range.getUpper();
                // LEGACY devices may report ranges multiplied by 1000
                if (upper >= 60 && upper < 1000 || upper >= 60_000) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Focal length relative to sensor width: proportional to 1 / field of view. 0 if unknown. */
    private static float normalizedFocal(@NonNull CameraCharacteristics characteristics) {
        float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focals == null || focals.length == 0 || sensor == null || sensor.getWidth() <= 0f) {
            return 0f;
        }
        return focals[0] / sensor.getWidth();
    }
}
