package ru.llacker.neogram.round;

import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Hooks of Telegram's round-video recorder (V2, org.telegram.utils.recorder.round) into Neogram's
 * camera policy and lens catalog.
 */
public final class NeoRoundCamera {

    private NeoRoundCamera() {
    }

    /**
     * Camera choice of RoundVideoCameraController.isPreferredCameraCandidate: for the back camera
     * prefer the logical multi-camera that reaches the widest field of view, so the ultra-wide is
     * available through CONTROL_ZOOM_RATIO. Returns null to keep the stock choice.
     */
    @Nullable
    public static Boolean isPreferredCamera(@NonNull CameraCharacteristics candidate,
                                            @NonNull CameraCharacteristics current, boolean back) {
        if (!back) {
            return null;
        }
        int compare = compareBackCameras(candidate, current);
        return compare == 0 ? null : compare > 0;
    }

    /** Positive when {@code a} is the better back camera for video messages. */
    static int compareBackCameras(@NonNull CameraCharacteristics a, @NonNull CameraCharacteristics b) {
        int byRange = Float.compare(minimumZoomRatio(b), minimumZoomRatio(a));
        if (byRange != 0) {
            return byRange;
        }
        return Boolean.compare(isLogicalMultiCamera(a), isLogicalMultiCamera(b));
    }

    static float minimumZoomRatio(@NonNull CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) {
                return range.getLower();
            }
        }
        return 1f;
    }

    static boolean isLogicalMultiCamera(@NonNull CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            return false;
        }
        for (int capability : capabilities) {
            if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                return true;
            }
        }
        return false;
    }

    /** Initial value of RoundVideoCameraController.warmSwitchDisabled is the inverse of this. */
    public static boolean isFastSwitchAllowed() {
        return NeoCameraPolicy.isFastSwitchAllowed();
    }
}
