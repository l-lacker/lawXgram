package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Session {

    private boolean isError;
    private boolean isSuccess;
    private boolean isClosed;

    private final CameraManager cameraManager;
    private final boolean isFront;
    public final String cameraId;
    public final String physicalCameraId;
    private CameraCharacteristics cameraCharacteristics;

    private HandlerThread thread;
    private Handler handler;

    private CameraDevice cameraDevice;
    private SurfaceTexture surfaceTexture;
    private CameraCaptureSession captureSession;
    private Surface surface;

    private final CameraDevice.StateCallback cameraStateCallback;
    private final CameraCaptureSession.StateCallback captureStateCallback;
    private CaptureRequest.Builder captureRequestBuilder;
    private Rect sensorSize;
    private boolean supportsZoomRatio;
    private float minZoom = 1f;
    private float maxZoom = 1f;
    private float currentZoom = 1f;
    private int targetFps = 30;

    private final Size previewSize;

    private ImageReader imageReader;

    private long lastTime;

    public static class CameraModule {
        public final String id;
        public final String cameraId;
        public final String physicalCameraId;
        public final boolean front;
        public final float focalLength;
        public final float zoomRatio;
        public final boolean main;
        public final int maxFps;
        public final int previewWidth;
        public final int previewHeight;

        private CameraModule(String id, String cameraId, String physicalCameraId, boolean front, float focalLength, float zoomRatio, boolean main, int maxFps, Size previewSize) {
            this.id = id;
            this.cameraId = cameraId;
            this.physicalCameraId = physicalCameraId;
            this.front = front;
            this.focalLength = focalLength;
            this.zoomRatio = zoomRatio;
            this.main = main;
            this.maxFps = maxFps;
            this.previewWidth = previewSize.getWidth();
            this.previewHeight = previewSize.getHeight();
        }
    }

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight) {
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        float bestAspectRatio = 0;
        Size bestSize = null;
        String cameraId = null;
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIds.length; ++i) {
                final String id = cameraIds[i];
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) continue;
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = (StreamConfigurationMap) characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                float cameraAspectRatio = pixelSize == null ? 0 : (float) pixelSize.getWidth() / pixelSize.getHeight();
                if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
                    cameraAspectRatio = 1f / cameraAspectRatio;
                }
                if (bestAspectRatio <= 0 || Math.abs((float) viewWidth / viewHeight - bestAspectRatio) > Math.abs((float) viewWidth / viewHeight - cameraAspectRatio)) {
                    if (confMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Size size = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
                        if (size != null) {
                            bestAspectRatio = cameraAspectRatio;
                            cameraId = id;
                            bestSize = size;
                        }
                    }
                } else {

                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (cameraId == null || bestSize == null) {
            return null;
        }
        return new Camera2Session(context, front, cameraId, null, 1f, bestSize);
    }

    public static Camera2Session create(String cameraId, int viewWidth, int viewHeight) {
        if (cameraId == null) {
            return null;
        }
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (characteristics == null) {
                return null;
            }
            StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (confMap == null) {
                return null;
            }
            Size bestSize = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            return new Camera2Session(context, facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT, cameraId, null, 1f, bestSize);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Camera2Session create(CameraModule module, int viewWidth, int viewHeight) {
        if (module == null) {
            return null;
        }
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(module.cameraId);
            if (characteristics == null) {
                return null;
            }
            StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (confMap == null) {
                return null;
            }
            Size bestSize = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            return new Camera2Session(context, facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT, module.cameraId, module.physicalCameraId, module.zoomRatio > 0 ? module.zoomRatio : 1f, bestSize);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static ArrayList<CameraModule> getCameraModules(boolean front, int viewWidth, int viewHeight) {
        ArrayList<CameraModule> modules = new ArrayList<>();
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            String defaultCameraId = findDefaultCameraId(cameraManager, cameraIds, front, viewWidth, viewHeight);
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) {
                    continue;
                }
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (confMap == null) {
                    continue;
                }
                Size size = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
                if (size == null) {
                    continue;
                }
                boolean main = id.equals(defaultCameraId);
                boolean addedLogical = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogicalMultiCamera(characteristics)) {
                    modules.add(new CameraModule(id, id, null, front, getFocalLength(characteristics), 0f, main, getMaxFps(characteristics), size));
                    addedLogical = true;
                    Set<String> physicalIds = characteristics.getPhysicalCameraIds();
                    if (physicalIds != null && !physicalIds.isEmpty()) {
                        for (String physicalId : physicalIds) {
                            try {
                                CameraCharacteristics physicalCharacteristics = getPhysicalCameraCharacteristics(cameraManager, id, physicalId, characteristics);
                                modules.add(new CameraModule(id + ":" + physicalId, id, physicalId, front, getFocalLength(physicalCharacteristics), 0f, false, getMaxFps(physicalCharacteristics), size));
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
                if (!addedLogical) {
                    modules.add(new CameraModule(id, id, null, front, getFocalLength(characteristics), 0f, main, getMaxFps(characteristics), size));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (!front && modules.size() == 1) {
            ArrayList<CameraModule> zoomModules = createZoomRatioModules(cameraManager, modules.get(0), viewWidth, viewHeight);
            if (zoomModules.size() > 1) {
                modules = zoomModules;
            }
        }
        Collections.sort(modules, (lhs, rhs) -> {
            if (lhs.zoomRatio > 0 && rhs.zoomRatio > 0) {
                return Float.compare(lhs.zoomRatio, rhs.zoomRatio);
            } else if (lhs.zoomRatio > 0) {
                return -1;
            } else if (rhs.zoomRatio > 0) {
                return 1;
            }
            if (lhs.focalLength > 0 && rhs.focalLength > 0) {
                return Float.compare(lhs.focalLength, rhs.focalLength);
            } else if (lhs.focalLength > 0) {
                return -1;
            } else if (rhs.focalLength > 0) {
                return 1;
            }
            return lhs.id.compareTo(rhs.id);
        });
        return modules;
    }

    private static String findDefaultCameraId(CameraManager cameraManager, String[] cameraIds, boolean front, int viewWidth, int viewHeight) {
        float bestAspectRatio = 0;
        String cameraId = null;
        try {
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) {
                    continue;
                }
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                float cameraAspectRatio = pixelSize == null ? 0 : (float) pixelSize.getWidth() / pixelSize.getHeight();
                if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
                    cameraAspectRatio = 1f / cameraAspectRatio;
                }
                if (bestAspectRatio <= 0 || Math.abs((float) viewWidth / viewHeight - bestAspectRatio) > Math.abs((float) viewWidth / viewHeight - cameraAspectRatio)) {
                    if (confMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false) != null) {
                        bestAspectRatio = cameraAspectRatio;
                        cameraId = id;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return cameraId;
    }

    private static ArrayList<CameraModule> createZoomRatioModules(CameraManager cameraManager, CameraModule baseModule, int viewWidth, int viewHeight) {
        ArrayList<CameraModule> modules = new ArrayList<>();
        if (baseModule == null || baseModule.cameraId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return modules;
        }
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(baseModule.cameraId);
            Range<Float> zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (zoomRange == null) {
                return modules;
            }
            float minZoom = zoomRange.getLower() == null ? 1f : zoomRange.getLower();
            float maxZoom = zoomRange.getUpper() == null ? 1f : zoomRange.getUpper();
            if (maxZoom < 1.2f && minZoom > 0.95f) {
                return modules;
            }
            StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size size = confMap != null ? chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false) : null;
            if (size == null) {
                return modules;
            }
            int maxFps = getMaxFps(characteristics);
            float baseFocal = baseModule.focalLength > 0 ? baseModule.focalLength : getFocalLength(characteristics);
            if (minZoom < 0.95f) {
                addZoomRatioModule(modules, baseModule, minZoom, baseFocal, maxFps, size);
            }
            addZoomRatioModule(modules, baseModule, 1f, baseFocal, maxFps, size);
            if (maxZoom >= 1.8f) {
                addZoomRatioModule(modules, baseModule, 2f, baseFocal, maxFps, size);
            }
            if (maxZoom >= 4f) {
                addZoomRatioModule(modules, baseModule, 4f, baseFocal, maxFps, size);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return modules;
    }

    private static void addZoomRatioModule(ArrayList<CameraModule> modules, CameraModule baseModule, float zoomRatio, float baseFocal, int maxFps, Size size) {
        for (int i = 0; i < modules.size(); i++) {
            if (Math.abs(modules.get(i).zoomRatio - zoomRatio) < 0.05f) {
                return;
            }
        }
        boolean main = Math.abs(zoomRatio - 1f) < 0.05f;
        float focalLength = main && baseFocal > 0 ? baseFocal : 0f;
        String id = baseModule.cameraId + ":zoom:" + Math.round(zoomRatio * 100f);
        modules.add(new CameraModule(id, baseModule.cameraId, null, baseModule.front, focalLength, zoomRatio, main, maxFps, size));
    }

    private static boolean isLogicalMultiCamera(CameraCharacteristics characteristics) {
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            return false;
        }
        for (int capability : capabilities) {
            if (capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                return true;
            }
        }
        return false;
    }

    private static CameraCharacteristics getPhysicalCameraCharacteristics(CameraManager cameraManager, String logicalId, String physicalId, CameraCharacteristics fallback) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return cameraManager.getCameraCharacteristics(physicalId);
        }
        for (String id : cameraManager.getCameraIdList()) {
            if (id.equals(physicalId)) {
                return cameraManager.getCameraCharacteristics(physicalId);
            }
        }
        return fallback;
    }

    private Camera2Session(Context context, boolean isFront, String cameraId, String physicalCameraId, float initialZoom, Size size) {
        thread = new HandlerThread("tg_camera2");
        thread.start();
        handler = new Handler(thread.getLooper());

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                Camera2Session.this.lastTime = System.currentTimeMillis();
                FileLog.d("Camera2Session camera #" + cameraId + " opened");
                checkOpen();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.d("Camera2Session camera #" + cameraId + " disconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.e("Camera2Session camera #" + cameraId + " received " + error + " error");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        captureStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session configured");
                Camera2Session.this.lastTime = System.currentTimeMillis();
                try {
                    updateCaptureRequest();
                    AndroidUtilities.runOnUIThread(() -> {
                        isSuccess = true;
                        if (doneCallback != null) {
                            doneCallback.run();
                            doneCallback = null;
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session failed to configure");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        this.isFront = isFront;
        this.cameraId = cameraId;
        this.physicalCameraId = physicalCameraId;
        this.previewSize = size;
        this.lastTime = System.currentTimeMillis();
        this.imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Range<Float> zoomRatioRange = cameraCharacteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (zoomRatioRange != null) {
                    supportsZoomRatio = true;
                    minZoom = Math.max(0.1f, zoomRatioRange.getLower());
                    maxZoom = Math.max(1f, zoomRatioRange.getUpper());
                }
            }
            if (!supportsZoomRatio) {
                final Float value = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                maxZoom = (value == null || value < 1f) ? 1f : value;
            }
            currentZoom = Utilities.clamp(initialZoom, maxZoom, minZoom);
            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
    }

    private Runnable doneCallback;
    public void whenDone(Runnable doneCallback) {
        if (isInitiated()) {
            doneCallback.run();
            this.doneCallback = null;
        } else {
            this.doneCallback = doneCallback;
        }
    }

    public void open(SurfaceTexture surfaceTexture) {
        handler.post(() -> {
            this.surfaceTexture = surfaceTexture;
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
            }
            checkOpen();
        });
    }

    private boolean opened = false;
    private void checkOpen() {
        if (opened) return;
        if (surfaceTexture == null || cameraDevice == null) return;
        opened = true;

        surface = new Surface(surfaceTexture);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
                ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
                OutputConfiguration previewOutput = new OutputConfiguration(surface);
                previewOutput.setPhysicalCameraId(physicalCameraId);
                outputConfigurations.add(previewOutput);
                Executor executor = command -> handler.post(command);
                cameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, executor, captureStateCallback));
            } else {
                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);
                surfaces.add(imageReader.getSurface());
                cameraDevice.createCaptureSession(surfaces, captureStateCallback, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
    }

    public boolean isInitiated() {
        return !isError && isSuccess && !isClosed;
    }

    public int getDisplayOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int displayOrientation;
            if (isFront) {
                displayOrientation = (sensorOrientation + degrees) % 360;
                displayOrientation = (360 - displayOrientation) % 360; // compensate the mirror
            } else { // back-facing
                displayOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return displayOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    private int getJpegOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int jpegOrientation;
            if (isFront) {
                jpegOrientation = (sensorOrientation + degrees) % 360;
                jpegOrientation = (360 - jpegOrientation) % 360; // compensate the mirror
            } else { // back-facing
                jpegOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return jpegOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public int getWorldAngle() {
        int displayOrientation = getDisplayOrientation();
        int jpegOrientation = getJpegOrientation();
        int diffOrientation = jpegOrientation - displayOrientation;
        if (diffOrientation < 0) {
            diffOrientation += 360;
        }
        return diffOrientation;
    }

    public int getCurrentOrientation() {
        return getJpegOrientation();
    }

    private final Rect cropRegion = new Rect();
    public void setZoom(float value) {
        if (!isInitiated()) return;
        if (captureRequestBuilder == null || cameraDevice == null || sensorSize == null) return;

        currentZoom = Utilities.clamp(value, maxZoom, minZoom);
        updateCaptureRequest();

        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean flashing;
    public void setFlash(boolean flash) {
        if (flashing != flash) {
            flashing = flash;
            updateCaptureRequest();
        }
    }
    public boolean getFlash() {
        return flashing;
    }

    public float getZoom() {
        return currentZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public float getMinZoom() {
        return minZoom;
    }

    public int getBestSupportedFps(int fpsCap) {
        Range<Integer> range = chooseBestFpsRange(cameraCharacteristics, fpsCap);
        return range != null ? range.getUpper() : fpsCap;
    }

    public void setTargetFps(int fps) {
        targetFps = Math.max(1, fps);
        updateCaptureRequest();
    }

    public int getPreviewWidth() {
        return previewSize.getWidth();
    }

    public int getPreviewHeight() {
        return previewSize.getHeight();
    }

    public void destroy(boolean async) {
        destroy(async, null);
    }

    public void destroy(boolean async, Runnable afterCallback) {
        isClosed = true;
        if (async) {
            handler.post(() -> {
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                thread.quitSafely();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        thread.join();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (afterCallback != null) {
                        afterCallback.run();
                    }
                });
            });
        } else {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            thread.quitSafely();
            try {
                thread.join();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (afterCallback != null) {
                AndroidUtilities.runOnUIThread(afterCallback);
            }
        }
    }

    private boolean recordingVideo;
    public void setRecordingVideo(boolean recording) {
        if (recordingVideo != recording) {
            recordingVideo = recording;
            updateCaptureRequest();
        }
    }

    private boolean scanningBarcode;
    public void setScanningBarcode(boolean scanning) {
        if (scanningBarcode != scanning) {
            scanningBarcode = scanning;
            updateCaptureRequest();
        }
    }

    private boolean nightMode;
    public void setNightMode(boolean enable) {
        if (nightMode != enable) {
            nightMode = enable;
            updateCaptureRequest();
        }
    }

    private void updateCaptureRequest() {
        if (cameraDevice == null || surface == null || captureSession == null) return;
        try {
            int template;
            if (recordingVideo) {
                template = CameraDevice.TEMPLATE_RECORD;
            } else if (scanningBarcode) {
                template = CameraDevice.TEMPLATE_STILL_CAPTURE;
            } else {
                template = CameraDevice.TEMPLATE_PREVIEW;
            }
            captureRequestBuilder = cameraDevice.createCaptureRequest(template);

            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            } else if (nightMode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, isFront ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
            }

            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, flashing ? (recordingVideo ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_SINGLE) : CaptureRequest.FLASH_MODE_OFF);

            if (recordingVideo) {
                Range<Integer> range = chooseBestFpsRange(cameraCharacteristics, targetFps);
                if (range != null) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range);
                }
                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
            }

            if (supportsZoomRatio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom);
            } else if (sensorSize != null && Math.abs(currentZoom - 1f) >= 0.01f) {
                final int centerX = sensorSize.width() / 2;
                final int centerY = sensorSize.height() / 2;
                final int deltaX = (int) ((0.5f * sensorSize.width()) / currentZoom);
                final int deltaY = (int) ((0.5f * sensorSize.height()) / currentZoom);
                cropRegion.set(
                        centerX - deltaX,
                        centerY - deltaY,
                        centerX + deltaX,
                        centerY + deltaY
                );
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
            }

            captureRequestBuilder.addTarget(surface);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (Exception e) {
            FileLog.e("Camera2Sessions setRepeatingRequest error in updateCaptureRequest", e);
        }
    }

    public boolean takePicture(final File file, Utilities.Callback<Integer> whenDone) {
        if (cameraDevice == null || captureSession == null) return false;
        try {
            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            final int orientation = getJpegOrientation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        image.close();
                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (whenDone != null) {
                            whenDone.run(orientation);
                        }
                    });
                }
            }, null);
            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            }
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {}, null);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions takePicture error", e);
            return false;
        }
    }


    public static Size chooseOptimalSize(Size[] choices, int width, int height, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> bigEnough = new ArrayList<>(choices.length);
        int w = width;
        int h = height;
        for (int a = 0; a < choices.length; a++) {
            Size option = choices[a];
            if (notBigger && (option.getHeight() > height || option.getWidth() > width)) {
                continue;
            }
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnoughWithAspectRatio.add(option);
            } else if (option.getHeight() * option.getWidth() <= width * height * 4 && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
        }
    }

    private static float getFocalLength(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focalLengths == null || focalLengths.length == 0) {
            return 0f;
        }
        float result = 0f;
        for (float focalLength : focalLengths) {
            if (focalLength > 0 && (result == 0f || focalLength < result)) {
                result = focalLength;
            }
        }
        return result;
    }

    private static int getMaxFps(CameraCharacteristics characteristics) {
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        int max = 0;
        if (ranges != null) {
            for (Range<Integer> range : ranges) {
                if (range != null) {
                    max = Math.max(max, range.getUpper());
                }
            }
        }
        return max;
    }

    private static Range<Integer> chooseBestFpsRange(CameraCharacteristics characteristics, int fpsCap) {
        if (characteristics == null) {
            return null;
        }
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null || ranges.length == 0) {
            return null;
        }
        Range<Integer> best = null;
        for (Range<Integer> range : ranges) {
            if (range == null || range.getUpper() > fpsCap) {
                continue;
            }
            if (best == null || range.getUpper() > best.getUpper() || range.getUpper().equals(best.getUpper()) && range.getLower() > best.getLower()) {
                best = range;
            }
        }
        if (best != null) {
            return best;
        }
        for (Range<Integer> range : ranges) {
            if (range == null) {
                continue;
            }
            if (best == null || range.getUpper() < best.getUpper()) {
                best = range;
            }
        }
        return best;
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

}
