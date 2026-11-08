package ru.llacker.neogram.round;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.utils.settings.SharedSettings;

import ru.llacker.neogram.config.NeoOptions;

/**
 * Decides how video messages are recorded on this device, so that Camera2 is used where it helps
 * and the app falls back by itself where it does not:
 * <ul>
 *     <li>the recorder choice is Telegram's own setting ({@code SharedSettings.roundVideoCamera2Enabled}):
 *     unset means "Auto" — the server switch, plus the device checks below;</li>
 *     <li>Auto keeps the classic recorder when the cameras are LEGACY-level (Camera2 emulated over
 *     the old HAL: few stream combinations, poor control) or when the new recorder failed to start
 *     repeatedly with this firmware and app version;</li>
 *     <li>no second camera kept open (instant front/back switch) in battery saver, when the device
 *     is hot or when the user turned it off;</li>
 *     <li>30 fps and a lighter camera mode in battery saver or under severe thermal load.</li>
 * </ul>
 */
public final class NeoCameraPolicy {

    /** Recorder choice of the settings page; AUTO is the stock setting left unset. */
    public enum RecorderMode { AUTO, NEW, CLASSIC }

    /** Why Auto records with the classic recorder on this device. */
    public enum AutoFallback {
        /** Telegram disabled the new recorder remotely. */
        SERVER,
        /** The cameras only offer the LEGACY Camera2 level. */
        LEGACY_CAMERA,
        /** The new recorder failed to start several times in a row. */
        START_FAILURES
    }

    /** Consecutive start failures of the new recorder after which Auto falls back. */
    static final int MAX_FAILURES = 3;
    /** Failures older than this are forgotten, so the new recorder gets another chance. */
    static final long FAILURE_MEMORY_MS = 7L * 24 * 60 * 60 * 1000;

    private static volatile Boolean camera2Suitable;

    private NeoCameraPolicy() {
    }

    @NonNull
    public static RecorderMode recorderMode() {
        if (!SharedSettings.roundVideoCamera2Enabled.isSet()) {
            return RecorderMode.AUTO;
        }
        return SharedSettings.roundVideoCamera2Enabled.get() ? RecorderMode.NEW : RecorderMode.CLASSIC;
    }

    public static void setRecorderMode(@NonNull RecorderMode mode) {
        switch (mode) {
            case NEW:
                SharedSettings.roundVideoCamera2Enabled.set(true);
                break;
            case CLASSIC:
                SharedSettings.roundVideoCamera2Enabled.set(false);
                break;
            default:
                SharedSettings.roundVideoCamera2Enabled.neo_reset();
                break;
        }
    }

    /** True to use the new Camera2 recorder (InstantCameraView2), false for the classic one. */
    public static boolean useNewRecorder(int account) {
        RecorderMode mode = recorderMode();
        boolean enabled = mode == RecorderMode.AUTO ? autoFallback(account) == null : mode == RecorderMode.NEW;
        if (enabled) {
            NeoRoundCamera.prepare();
        }
        return enabled;
    }

    /** Null when Auto would use the new recorder, else the reason it would not. */
    @Nullable
    public static AutoFallback autoFallback(int account) {
        if (AppGlobalConfig.getInstance(account).roundVideoRecorderV2Disabled.get()) {
            return AutoFallback.SERVER;
        }
        if (!isCamera2Suitable()) {
            return AutoFallback.LEGACY_CAMERA;
        }
        if (isFailingOnThisBuild()) {
            return AutoFallback.START_FAILURES;
        }
        return null;
    }

    /** False when the first camera of either facing only offers the LEGACY Camera2 level. */
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

    // ---- start failures -----------------------------------------------------------------------

    /** Firmware and app version: either changing gives the new recorder a fresh start. */
    @NonNull
    private static String buildKey() {
        return Build.FINGERPRINT + '#' + BuildConfig.BUILD_VERSION_STRING + '.' + BuildConfig.NEO_REVISION;
    }

    private static boolean isFailingOnThisBuild() {
        return buildKey().equals(NeoOptions.ROUND_V2_FAILURES_BUILD.get())
                && NeoOptions.ROUND_V2_FAILURES.value() >= MAX_FAILURES
                && System.currentTimeMillis() - NeoOptions.ROUND_V2_LAST_FAILURE.value() < FAILURE_MEMORY_MS;
    }

    /** The new recorder could not start. Errors any recorder would hit are not counted. */
    public static void onNewRecorderFailed(@Nullable Throwable error) {
        if (isEnvironmental(error)) {
            return;
        }
        String key = buildKey();
        int failures = key.equals(NeoOptions.ROUND_V2_FAILURES_BUILD.get()) ? NeoOptions.ROUND_V2_FAILURES.value() : 0;
        NeoOptions.ROUND_V2_FAILURES_BUILD.set(key);
        NeoOptions.ROUND_V2_FAILURES.set(failures + 1);
        NeoOptions.ROUND_V2_LAST_FAILURE.set(System.currentTimeMillis());
        if (failures + 1 >= MAX_FAILURES) {
            FileLog.d("neo camera: the new recorder failed " + (failures + 1) + " times, Auto switches to the classic one");
        }
    }

    /** The new recorder delivered a recording: forget earlier failures. */
    public static void onNewRecorderWorked() {
        if (NeoOptions.ROUND_V2_FAILURES.value() != 0) {
            NeoOptions.ROUND_V2_FAILURES.set(0);
        }
    }

    /**
     * Missing permissions and a camera that is busy, disabled by policy or taken by another app fail
     * any recorder, so they say nothing about the new one.
     */
    static boolean isEnvironmental(@Nullable Throwable error) {
        int depth = 0;
        for (Throwable e = error; e != null && depth < 8; e = e.getCause(), depth++) {
            if (e instanceof SecurityException) {
                return true;
            }
            if (e instanceof CameraAccessException) {
                int reason = ((CameraAccessException) e).getReason();
                return reason == CameraAccessException.CAMERA_IN_USE
                        || reason == CameraAccessException.MAX_CAMERAS_IN_USE
                        || reason == CameraAccessException.CAMERA_DISABLED
                        || reason == CameraAccessException.CAMERA_DISCONNECTED;
            }
        }
        return false;
    }

    // ---- load ---------------------------------------------------------------------------------

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
