package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private int maxAfRegions;
    private int maxAeRegions;
    private int[] availableAfModes;
    private int[] availableAeModes;
    private int[] availableSceneModes;
    private String currentFlashMode;
    public final ArrayList<String> availableFlashModes = new ArrayList<>();
    private MeteringRectangle focusArea;
    private MeteringRectangle meteringArea;
    private boolean flipFront = true;
    private boolean sameTakePictureOrientation = true;
    private OrientationEventListener orientationEventListener;
    private int jpegOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private int lastOrientation = -1;
    private int lastDisplayOrientation = -1;

    private final Size previewSize;
    private final Size pictureSize;
    private final boolean withStillCapture;

    private ImageReader imageReader;

    private long lastTime;

    public static class CameraModule {
        public final String id;
        public final String cameraId;
        public final String physicalCameraId;
        public final boolean front;
        public final float focalLength;
        public final boolean main;
        public final int maxFps;
        public final int previewWidth;
        public final int previewHeight;

        private CameraModule(String id, String cameraId, String physicalCameraId, boolean front, float focalLength, boolean main, int maxFps, Size previewSize) {
            this.id = id;
            this.cameraId = cameraId;
            this.physicalCameraId = physicalCameraId;
            this.front = front;
            this.focalLength = focalLength;
            this.main = main;
            this.maxFps = maxFps;
            this.previewWidth = previewSize.getWidth();
            this.previewHeight = previewSize.getHeight();
        }
    }

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight) {
        return create(front, viewWidth, viewHeight, viewWidth, viewHeight, false);
    }

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight) {
        return create(front, viewWidth, viewHeight, pictureWidth, pictureHeight, true);
    }

    private static Camera2Session create(boolean front, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight, boolean withStillCapture) {
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        float bestAspectRatio = 0;
        Size bestSize = null;
        Size bestPictureSize = null;
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
                        Size size = chooseOptimalSurfaceTextureSize(confMap, viewWidth, viewHeight);
                        if (size != null) {
                            bestAspectRatio = cameraAspectRatio;
                            cameraId = id;
                            bestSize = size;
                            bestPictureSize = chooseOptimalJpegSize(confMap, pictureWidth, pictureHeight, size);
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
        return new Camera2Session(context, front, cameraId, null, 1f, bestSize, bestPictureSize, withStillCapture);
    }

    public static Camera2Session create(String cameraId, int viewWidth, int viewHeight) {
        return create(cameraId, viewWidth, viewHeight, viewWidth, viewHeight, false);
    }

    public static Camera2Session create(String cameraId, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight) {
        return create(cameraId, viewWidth, viewHeight, pictureWidth, pictureHeight, true);
    }

    private static Camera2Session create(String cameraId, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight, boolean withStillCapture) {
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
            Size bestSize = chooseOptimalSurfaceTextureSize(confMap, viewWidth, viewHeight);
            if (bestSize == null) {
                return null;
            }
            Size bestPictureSize = chooseOptimalJpegSize(confMap, pictureWidth, pictureHeight, bestSize);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            return new Camera2Session(context, facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT, cameraId, null, 1f, bestSize, bestPictureSize, withStillCapture);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Camera2Session create(CameraModule module, int viewWidth, int viewHeight) {
        return create(module, viewWidth, viewHeight, viewWidth, viewHeight, false);
    }

    public static Camera2Session create(CameraModule module, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight) {
        return create(module, viewWidth, viewHeight, pictureWidth, pictureHeight, true);
    }

    private static Camera2Session create(CameraModule module, int viewWidth, int viewHeight, int pictureWidth, int pictureHeight, boolean withStillCapture) {
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
            Size bestSize = chooseOptimalSurfaceTextureSize(confMap, viewWidth, viewHeight);
            if (bestSize == null) {
                return null;
            }
            Size bestPictureSize = chooseOptimalJpegSize(confMap, pictureWidth, pictureHeight, bestSize);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            return new Camera2Session(context, facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT, module.cameraId, module.physicalCameraId, 1f, bestSize, bestPictureSize, withStillCapture);
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
            Set<String> publicCameraIds = new HashSet<>(Arrays.asList(cameraIds));
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
                Size size = chooseOptimalSurfaceTextureSize(confMap, viewWidth, viewHeight);
                if (size == null) {
                    continue;
                }
                boolean main = id.equals(defaultCameraId);
                boolean addedLogical = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogicalMultiCamera(characteristics)) {
                    addCameraModule(modules, new CameraModule(id, id, null, front, getFocalLength(characteristics), main, getMaxFps(characteristics), size));
                    addedLogical = true;
                    Set<String> physicalIds = characteristics.getPhysicalCameraIds();
                    if (physicalIds != null && !physicalIds.isEmpty()) {
                        for (String physicalId : physicalIds) {
                            if (publicCameraIds.contains(physicalId)) {
                                continue;
                            }
                            try {
                                CameraCharacteristics physicalCharacteristics = getPhysicalCameraCharacteristics(cameraManager, id, physicalId, characteristics);
                                addCameraModule(modules, new CameraModule(id + ":" + physicalId, id, physicalId, front, getFocalLength(physicalCharacteristics), false, getMaxFps(physicalCharacteristics), size));
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
                if (!addedLogical) {
                    addCameraModule(modules, new CameraModule(id, id, null, front, getFocalLength(characteristics), main, getMaxFps(characteristics), size));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        Collections.sort(modules, (lhs, rhs) -> {
            if (lhs.main != rhs.main) {
                return lhs.main ? -1 : 1;
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

    private static void addCameraModule(ArrayList<CameraModule> modules, CameraModule module) {
        if (module == null) {
            return;
        }
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).id.equals(module.id)) {
                return;
            }
        }
        modules.add(module);
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
                    if (confMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && chooseOptimalSurfaceTextureSize(confMap, viewWidth, viewHeight) != null) {
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

    private Camera2Session(Context context, boolean isFront, String cameraId, String physicalCameraId, float initialZoom, Size size, Size pictureSize, boolean withStillCapture) {
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
                notifyError();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.e("Camera2Session camera #" + cameraId + " received " + error + " error");
                notifyError();
            }
        };

        captureStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session configured");
                Camera2Session.this.lastTime = System.currentTimeMillis();
                try {
                    if (!updateCaptureRequest()) {
                        notifyError();
                        return;
                    }
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
                notifyError();
            }
        };

        this.isFront = isFront;
        this.cameraId = cameraId;
        this.physicalCameraId = physicalCameraId;
        this.previewSize = size;
        this.pictureSize = pictureSize != null ? pictureSize : size;
        this.withStillCapture = withStillCapture;
        this.lastTime = System.currentTimeMillis();
        this.imageReader = withStillCapture ? ImageReader.newInstance(this.pictureSize.getWidth(), this.pictureSize.getHeight(), ImageFormat.JPEG, 1) : null;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer afRegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            Integer aeRegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            maxAfRegions = afRegions == null ? 0 : afRegions;
            maxAeRegions = aeRegions == null ? 0 : aeRegions;
            availableAfModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            availableAeModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
            availableSceneModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
            initFlashModes();
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
            initOrientationListener();
            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (Exception e) {
            FileLog.e(e);
            notifyError();
        }
    }

    private void initFlashModes() {
        availableFlashModes.clear();
        availableFlashModes.add(android.hardware.Camera.Parameters.FLASH_MODE_OFF);
        Boolean flashAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (Boolean.TRUE.equals(flashAvailable)) {
            availableFlashModes.add(android.hardware.Camera.Parameters.FLASH_MODE_ON);
            availableFlashModes.add(android.hardware.Camera.Parameters.FLASH_MODE_AUTO);
        }
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
        currentFlashMode = sharedPreferences.getString(isFront ? "flashMode_front" : "flashMode", android.hardware.Camera.Parameters.FLASH_MODE_OFF);
        checkFlashMode(availableFlashModes.get(0));
    }

    private void initOrientationListener() {
        orientationEventListener = new OrientationEventListener(ApplicationLoader.applicationContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientationEventListener == null || !isInitiated() || orientation == ORIENTATION_UNKNOWN) {
                    return;
                }
                jpegOrientation = roundOrientation(orientation, jpegOrientation);
                WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                int rotation = mgr.getDefaultDisplay().getRotation();
                if (lastOrientation != jpegOrientation || rotation != lastDisplayOrientation) {
                    updateRotation();
                    lastDisplayOrientation = rotation;
                    lastOrientation = jpegOrientation;
                }
            }
        };
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    private Runnable doneCallback;
    private Runnable errorCallback;

    public void setErrorCallback(Runnable errorCallback) {
        this.errorCallback = errorCallback;
        if (isError && errorCallback != null) {
            AndroidUtilities.runOnUIThread(errorCallback);
        }
    }

    private void notifyError() {
        AndroidUtilities.runOnUIThread(() -> {
            if (isError || isClosed) {
                return;
            }
            isError = true;
            if (errorCallback != null) {
                errorCallback.run();
            }
        });
    }

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
                if (imageReader != null) {
                    OutputConfiguration imageOutput = new OutputConfiguration(imageReader.getSurface());
                    imageOutput.setPhysicalCameraId(physicalCameraId);
                    outputConfigurations.add(imageOutput);
                }
                Executor executor = command -> handler.post(command);
                cameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, executor, captureStateCallback));
            } else {
                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);
                if (imageReader != null) {
                    surfaces.add(imageReader.getSurface());
                }
                cameraDevice.createCaptureSession(surfaces, captureStateCallback, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
            notifyError();
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
            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation == null) {
                return 0;
            }
            if (jpegOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                return getJpegOrientation(sensorOrientation, jpegOrientation);
            }
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int degrees = getDisplayRotationDegrees(context);
            return getJpegOrientation(sensorOrientation, (360 - degrees) % 360);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    private int getJpegOrientation(int sensorOrientation, int deviceOrientation) {
        if (isFront) {
            deviceOrientation = -deviceOrientation;
        }
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private int getSensorBasedCaptureOrientation() {
        try {
            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation == null) {
                return 0;
            }
            if (sensorOrientation % 90 != 0) {
                sensorOrientation = 0;
            }
            if (isFront) {
                return (360 - sensorOrientation) % 360;
            } else {
                return sensorOrientation;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    private int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = dist >= 45 + CameraSession.ORIENTATION_HYSTERESIS;
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private int getDisplayRotationDegrees(Context context) {
        int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    public int getWorldAngle() {
        int displayOrientation = getDisplayOrientation();
        int captureOrientation = getSensorBasedCaptureOrientation();
        int diffOrientation = captureOrientation - displayOrientation;
        if (diffOrientation < 0) {
            diffOrientation += 360;
        }
        return diffOrientation;
    }

    public int getCurrentOrientation() {
        return getSensorBasedCaptureOrientation();
    }

    public boolean isSameTakePictureOrientation() {
        updateRotation();
        return sameTakePictureOrientation;
    }

    public void updateRotation() {
        int displayOrientation = getDisplayOrientation();
        int outputOrientation = getJpegOrientation();
        if (isFront) {
            sameTakePictureOrientation = (360 - displayOrientation) % 360 == outputOrientation;
        } else {
            sameTakePictureOrientation = displayOrientation == outputOrientation;
        }
    }

    private final Rect cropRegion = new Rect();
    public void setZoom(float value) {
        if (!isInitiated()) return;
        if (captureRequestBuilder == null || cameraDevice == null || sensorSize == null) return;

        currentZoom = Utilities.clamp(value, maxZoom, minZoom);
        updateCaptureRequest();
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

    public void checkFlashMode(String mode) {
        if (availableFlashModes.contains(currentFlashMode) || Camera.Parameters.FLASH_MODE_TORCH.equals(currentFlashMode)) {
            return;
        }
        setCurrentFlashMode(mode, false);
    }

    public void setCurrentFlashMode(String mode) {
        setCurrentFlashMode(mode, true);
    }

    private void setCurrentFlashMode(String mode, boolean save) {
        if (TextUtils.isEmpty(mode)) {
            mode = Camera.Parameters.FLASH_MODE_OFF;
        }
        if (!availableFlashModes.contains(mode) && !Camera.Parameters.FLASH_MODE_TORCH.equals(mode)) {
            mode = availableFlashModes.isEmpty() ? Camera.Parameters.FLASH_MODE_OFF : availableFlashModes.get(0);
        }
        if (TextUtils.equals(currentFlashMode, mode)) {
            return;
        }
        currentFlashMode = mode;
        updateCaptureRequest();
        if (save && !Camera.Parameters.FLASH_MODE_TORCH.equals(mode)) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
            sharedPreferences.edit().putString(isFront ? "flashMode_front" : "flashMode", mode).commit();
        }
    }

    public String getCurrentFlashMode() {
        return currentFlashMode;
    }

    public String getNextFlashMode() {
        for (int a = 0; a < availableFlashModes.size(); a++) {
            String mode = availableFlashModes.get(a);
            if (mode.equals(currentFlashMode)) {
                if (a < availableFlashModes.size() - 1) {
                    return availableFlashModes.get(a + 1);
                } else {
                    return availableFlashModes.get(0);
                }
            }
        }
        return availableFlashModes.isEmpty() ? Camera.Parameters.FLASH_MODE_OFF : availableFlashModes.get(0);
    }

    public boolean hasFlashModes() {
        return availableFlashModes.size() > 1;
    }

    public void setFlipFront(boolean flipFront) {
        this.flipFront = flipFront;
    }

    public boolean isFlipFront() {
        return flipFront;
    }

    public void focusToRect(Rect focusRect, Rect meteringRect) {
        if (!isInitiated() || sensorSize == null) {
            return;
        }
        MeteringRectangle focus = maxAfRegions > 0 ? toMeteringRectangle(focusRect) : null;
        MeteringRectangle metering = maxAeRegions > 0 ? toMeteringRectangle(meteringRect) : null;
        if (focus == null && metering == null) {
            return;
        }
        focusArea = focus;
        meteringArea = metering;
        updateCaptureRequest();
        triggerFocus();
    }

    private MeteringRectangle toMeteringRectangle(Rect camera1Rect) {
        if (camera1Rect == null || sensorSize == null) {
            return null;
        }
        Rect base = getMeteringBaseRect();
        int left = base.left + Math.round((Utilities.clamp(camera1Rect.left, 1000, -1000) + 1000) / 2000f * base.width());
        int top = base.top + Math.round((Utilities.clamp(camera1Rect.top, 1000, -1000) + 1000) / 2000f * base.height());
        int right = base.left + Math.round((Utilities.clamp(camera1Rect.right, 1000, -1000) + 1000) / 2000f * base.width());
        int bottom = base.top + Math.round((Utilities.clamp(camera1Rect.bottom, 1000, -1000) + 1000) / 2000f * base.height());
        left = Utilities.clamp(left, base.right - 1, base.left);
        top = Utilities.clamp(top, base.bottom - 1, base.top);
        right = Utilities.clamp(right, base.right, left + 1);
        bottom = Utilities.clamp(bottom, base.bottom, top + 1);
        return new MeteringRectangle(new Rect(left, top, right, bottom), MeteringRectangle.METERING_WEIGHT_MAX);
    }

    private Rect getMeteringBaseRect() {
        if (!supportsZoomRatio && !cropRegion.isEmpty()) {
            return new Rect(cropRegion);
        }
        return new Rect(sensorSize);
    }

    private void triggerFocus() {
        if (cameraDevice == null || captureSession == null || surface == null || previewPaused) {
            return;
        }
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(recordingVideo ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
            applyCommonRequestState(builder, false);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            builder.addTarget(surface);
            captureSession.capture(builder.build(), null, handler);
            if (captureRequestBuilder != null) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
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
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
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
    private boolean previewPaused;

    public void startPreview() {
        if (!previewPaused) {
            return;
        }
        previewPaused = false;
        updateCaptureRequest();
    }

    public void stopPreview() {
        if (previewPaused) {
            return;
        }
        previewPaused = true;
        if (captureSession == null) {
            return;
        }
        try {
            captureSession.stopRepeating();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

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

    private boolean updateCaptureRequest() {
        if (cameraDevice == null || surface == null || captureSession == null || previewPaused) return true;
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
            applyCommonRequestState(captureRequestBuilder, false);
            captureRequestBuilder.addTarget(surface);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions setRepeatingRequest error in updateCaptureRequest", e);
            notifyError();
            return false;
        }
    }

    private void applyCommonRequestState(CaptureRequest.Builder builder, boolean stillCapture) {
        if (builder == null) {
            return;
        }
        if (scanningBarcode && supportsSceneMode(CameraMetadata.CONTROL_SCENE_MODE_BARCODE)) {
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
        } else if (nightMode) {
            int nightSceneMode = isFront && supportsSceneMode(CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT) ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT;
            if (!supportsSceneMode(nightSceneMode)) {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, nightSceneMode);
            }
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        }

        applyFlashState(builder, stillCapture);
        applyFocusState(builder);
        applyZoomState(builder);

        if (recordingVideo) {
            Range<Integer> range = chooseBestFpsRange(cameraCharacteristics, targetFps);
            if (range != null) {
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range);
            }
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
        }
    }

    private boolean supportsSceneMode(int mode) {
        if (availableSceneModes == null) {
            return false;
        }
        for (int availableMode : availableSceneModes) {
            if (availableMode == mode) {
                return true;
            }
        }
        return false;
    }

    private void applyFlashState(CaptureRequest.Builder builder, boolean stillCapture) {
        boolean hasFlash = hasFlashModes();
        int aeMode = CaptureRequest.CONTROL_AE_MODE_ON;
        int flashMode = CaptureRequest.FLASH_MODE_OFF;
        boolean torch = hasFlash && (flashing || Camera.Parameters.FLASH_MODE_TORCH.equals(currentFlashMode) || recordingVideo && Camera.Parameters.FLASH_MODE_ON.equals(currentFlashMode));
        if (torch) {
            flashMode = CaptureRequest.FLASH_MODE_TORCH;
        } else if (hasFlash && stillCapture && Camera.Parameters.FLASH_MODE_ON.equals(currentFlashMode)) {
            aeMode = supportsAeMode(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) ? CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH : CaptureRequest.CONTROL_AE_MODE_ON;
        } else if (hasFlash && stillCapture && Camera.Parameters.FLASH_MODE_AUTO.equals(currentFlashMode)) {
            aeMode = supportsAeMode(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) ? CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH : CaptureRequest.CONTROL_AE_MODE_ON;
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
        builder.set(CaptureRequest.FLASH_MODE, flashMode);
    }

    private void applyFocusState(CaptureRequest.Builder builder) {
        int afMode = chooseContinuousAfMode();
        if (focusArea != null && supportsAfMode(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            afMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
        if (focusArea != null && maxAfRegions > 0) {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusArea});
        }
        if (meteringArea != null && maxAeRegions > 0) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{meteringArea});
        }
    }

    private void applyZoomState(CaptureRequest.Builder builder) {
        cropRegion.setEmpty();
        if (supportsZoomRatio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom);
        } else if (sensorSize != null && Math.abs(currentZoom - 1f) >= 0.01f) {
            final int centerX = sensorSize.centerX();
            final int centerY = sensorSize.centerY();
            final int deltaX = (int) ((0.5f * sensorSize.width()) / currentZoom);
            final int deltaY = (int) ((0.5f * sensorSize.height()) / currentZoom);
            cropRegion.set(
                    centerX - deltaX,
                    centerY - deltaY,
                    centerX + deltaX,
                    centerY + deltaY
            );
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
        }
    }

    private int chooseContinuousAfMode() {
        if (recordingVideo || scanningBarcode) {
            if (supportsAfMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                return CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
            }
        }
        if (supportsAfMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            return CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        }
        if (supportsAfMode(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            return CaptureRequest.CONTROL_AF_MODE_AUTO;
        }
        return CaptureRequest.CONTROL_AF_MODE_OFF;
    }

    private boolean supportsAfMode(int mode) {
        if (availableAfModes == null) {
            return false;
        }
        for (int availableMode : availableAfModes) {
            if (availableMode == mode) {
                return true;
            }
        }
        return false;
    }

    private boolean supportsAeMode(int mode) {
        if (availableAeModes == null) {
            return mode == CaptureRequest.CONTROL_AE_MODE_ON;
        }
        for (int availableMode : availableAeModes) {
            if (availableMode == mode) {
                return true;
            }
        }
        return false;
    }

    public boolean takePicture(final File file, final boolean ignoreOrientation, Utilities.Callback<Integer> whenDone) {
        if (!withStillCapture || imageReader == null || cameraDevice == null || captureSession == null) return false;
        try {
            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            applyCommonRequestState(captureRequestBuilder, true);
            final int requestedOrientation = getJpegOrientation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, requestedOrientation);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    int imageOrientation = CameraController.getOrientation(bytes);
                    int resultOrientation = imageOrientation != -1 ? imageOrientation : 0;

                    FileOutputStream output = null;
                    boolean written = false;
                    try {
                        if (isFront && flipFront) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPurgeable = true;
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                            if (bitmap != null) {
                                Matrix matrix = new Matrix();
                                if (!ignoreOrientation && imageOrientation != -1 && imageOrientation != 0) {
                                    matrix.setRotate(imageOrientation);
                                }
                                matrix.postScale(-1, 1);
                                Bitmap scaled = Bitmaps.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                if (scaled != bitmap) {
                                    bitmap.recycle();
                                }
                                output = new FileOutputStream(file);
                                scaled.compress(Bitmap.CompressFormat.JPEG, 80, output);
                                output.flush();
                                output.getFD().sync();
                                written = true;
                                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                                String key = String.format(Locale.US, "%s@%d_%d", Utilities.MD5(file.getAbsolutePath()), size, size);
                                ImageLoader.getInstance().putImageToCache(new BitmapDrawable(scaled), key, false);
                                resultOrientation = 0;
                            }
                        }
                        if (!written) {
                            if (output != null) {
                                output.close();
                                output = null;
                            }
                            output = new FileOutputStream(file);
                            output.write(bytes);
                            output.flush();
                            output.getFD().sync();
                            written = true;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                        if (!written) {
                            try {
                                if (output != null) {
                                    output.close();
                                }
                                output = new FileOutputStream(file);
                                output.write(bytes);
                                output.flush();
                                output.getFD().sync();
                            } catch (Throwable fallbackError) {
                                FileLog.e(fallbackError);
                            }
                        }
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

                    final int callbackOrientation = resultOrientation;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (whenDone != null) {
                            whenDone.run(callbackOrientation);
                        }
                    });
                }
            }, handler);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {}, handler);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions takePicture error", e);
            return false;
        }
    }


    private static Size chooseOptimalSurfaceTextureSize(StreamConfigurationMap confMap, int width, int height) {
        if (confMap == null) {
            return null;
        }
        Size[] outputSizes = confMap.getOutputSizes(SurfaceTexture.class);
        if (outputSizes == null || outputSizes.length == 0) {
            return null;
        }
        return chooseOptimalSize(outputSizes, width, height, false);
    }

    private static Size chooseOptimalJpegSize(StreamConfigurationMap confMap, int width, int height, Size fallback) {
        if (confMap == null) {
            return fallback;
        }
        Size[] outputSizes = confMap.getOutputSizes(ImageFormat.JPEG);
        if (outputSizes == null || outputSizes.length == 0) {
            return fallback;
        }
        return chooseOptimalSize(outputSizes, width, height, false);
    }

    public static Size chooseOptimalSize(Size[] choices, int width, int height, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> bigEnough = new ArrayList<>(choices.length);
        List<Size> notBiggerWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> notBiggerAny = new ArrayList<>(choices.length);
        int targetLong = Math.max(width, height);
        int targetShort = Math.min(width, height);
        long targetArea = (long) targetLong * targetShort;
        for (Size option : choices) {
            int optionLong = Math.max(option.getWidth(), option.getHeight());
            int optionShort = Math.min(option.getWidth(), option.getHeight());
            boolean optionNotBigger = optionLong <= targetLong && optionShort <= targetShort;
            if (notBigger && !optionNotBigger) {
                continue;
            }
            boolean aspectMatches = optionLong * targetShort == optionShort * targetLong;
            boolean optionBigEnough = optionLong >= targetLong && optionShort >= targetShort;
            long optionArea = (long) optionLong * optionShort;
            if (optionBigEnough && aspectMatches) {
                bigEnoughWithAspectRatio.add(option);
            } else if (optionBigEnough && optionArea <= targetArea * 4) {
                bigEnough.add(option);
            } else if (optionNotBigger) {
                if (aspectMatches) {
                    notBiggerWithAspectRatio.add(option);
                }
                notBiggerAny.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBiggerWithAspectRatio.size() > 0) {
            return Collections.max(notBiggerWithAspectRatio, new CompareSizesByArea());
        } else if (notBiggerAny.size() > 0) {
            return Collections.max(notBiggerAny, new CompareSizesByArea());
        } else {
            return Collections.min(Arrays.asList(choices), new CompareSizesByArea());
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
