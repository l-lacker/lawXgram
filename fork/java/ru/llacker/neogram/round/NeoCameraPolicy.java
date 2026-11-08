package ru.llacker.neogram.round;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.PowerManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.InstantCameraViewBase;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Decides how video messages are recorded on this device, so that Camera2 is used where it helps
 * and the app falls back by itself where it does not:
 * <ul>
 *     <li>Telegram's new Camera2 recorder (V2) unless the user chose otherwise, the cameras are
 *     LEGACY-level (Camera2 emulated over the old HAL: few stream combinations, poor control) or V2
 *     failed to start repeatedly on this firmware;</li>
 *     <li>no second camera kept open (instant front/back switch) in battery saver, when the device
 *     is hot or when the user turned it off;</li>
 *     <li>30 fps and a lighter camera mode in battery saver or under severe thermal load.</li>
 * </ul>
 */
public final class NeoCameraPolicy {

    /** Consecutive start failures of V2 after which AUTO falls back to the classic recorder. */
    static final int MAX_FAILURES = 2;

    private static volatile Boolean camera2Suitable;

    private NeoCameraPolicy() {
    }

    /** True to use the new Camera2 recorder (InstantCameraView2), false for the classic one. */
    public static boolean useNewRecorder(int account) {
        switch (NeoOptions.ROUND_RECORDER.get()) {
            case NEW:
                return true;
            case CLASSIC:
                return false;
            default:
                return InstantCameraViewBase.isCamera2Enabled(account) && isCamera2Suitable() && !isFailingOnThisFirmware();
        }
    }

    /** False when a camera of either facing only offers the LEGACY Camera2 level. */
    public static boolean isCamera2Suitable() {
        Boolean cached = camera2Suitable;
        if (cached == null) {
            cached = camera2Suitable = queryCamera2Suitable();
        }
        return cached;
    }

    private static boolean queryCamera2Suitable() {
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            boolean front = false;
            boolean back = false;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    continue;
                }
                boolean isFront = facing == CameraCharacteristics.LENS_FACING_FRONT;
                if (isFront ? front : back) {
                    continue; // the first camera of each facing is the one apps get by default
                }
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    FileLog.d("neo camera: " + (isFront ? "front" : "back") + " camera " + id + " is LEGACY, classic recorder");
                    return false;
                }
                if (isFront) {
                    front = true;
                } else {
                    back = true;
                }
            }
            return true;
        } catch (Throwable e) {
            // the camera service is unavailable right now: let the stock logic decide
            FileLog.e(e);
            return true;
        }
    }

    private static boolean isFailingOnThisFirmware() {
        return Build.FINGERPRINT.equals(NeoOptions.ROUND_V2_FAILURES_FIRMWARE.get())
                && NeoOptions.ROUND_V2_FAILURES.value() >= MAX_FAILURES;
    }

    /** The new recorder could not start (camera/encoder error before the first frame). */
    public static void onNewRecorderFailed() {
        if (!Build.FINGERPRINT.equals(NeoOptions.ROUND_V2_FAILURES_FIRMWARE.get())) {
            NeoOptions.ROUND_V2_FAILURES_FIRMWARE.set(Build.FINGERPRINT);
            NeoOptions.ROUND_V2_FAILURES.set(0);
        }
        NeoOptions.ROUND_V2_FAILURES.set(NeoOptions.ROUND_V2_FAILURES.value() + 1);
    }

    /** The new recorder delivered frames: forget earlier failures. */
    public static void onNewRecorderWorked() {
        if (NeoOptions.ROUND_V2_FAILURES.value() != 0) {
            NeoOptions.ROUND_V2_FAILURES.set(0);
        }
    }

    /** Whether the instant front/back switch may keep the second camera open. */
    public static boolean isFastSwitchAllowed() {
        if (NeoOptions.ROUND_FAST_SWITCH.get() == NeoOptions.FastCameraSwitch.OFF) {
            return false;
        }
        return !isPowerSaving() && thermalStatus() < PowerManager.THERMAL_STATUS_MODERATE;
    }

    /** Battery saver or severe heat: record at 30 fps with a lighter camera mode. */
    public static boolean isConstrained() {
        return isPowerSaving() || thermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
    }

    private static boolean isPowerSaving() {
        PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    private static int thermalStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
        PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        return pm == null ? PowerManager.THERMAL_STATUS_NONE : pm.getCurrentThermalStatus();
    }
}
