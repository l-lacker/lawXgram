package ru.llacker.neogram.round;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.core.round.LensMath;
import ru.llacker.neogram.core.round.RoundSpec;

/**
 * Hooks of Telegram's round-video recorder (V2, {@code org.telegram.utils.recorder.round}) into
 * Neogram's camera policy and lens catalog. Called from the stock code by fully-qualified name.
 */
public final class NeoRoundCamera {

    private NeoRoundCamera() {
    }

    /** The new recorder was chosen for a chat: read the encoder and the lenses in the background. */
    static void prepare() {
        NeoRoundEncoder.warmUp();
        if (NeoOptions.ROUND_LENS_SWITCHER.value()) {
            NeoLensCatalog.warmUp();
        }
    }

    /**
     * Output size of a new session (InstantCameraView2.showCamera): the stock setting unless the
     * device encoder rejects it (16-aligned encoders and 360×360), then 480×480.
     */
    @NonNull
    public static RoundVideoSession.OutputResolution outputResolution(@NonNull RoundVideoSession.OutputResolution requested) {
        if (requested != RoundVideoSession.OutputResolution.P480
                && Boolean.FALSE.equals(NeoRoundEncoder.supports(requested.getSize(), 30))
                && Boolean.TRUE.equals(NeoRoundEncoder.supports(RoundVideoSession.OutputResolution.P480.getSize(), 30))) {
            return RoundVideoSession.OutputResolution.P480;
        }
        return requested;
    }

    /**
     * Side of the camera square scaled into a 560 or 624 output (RoundVideoCameraController
     * .getSourceCropSize; the stock sizes keep their own table): twice the output in High, capped at
     * 1080, the short side of the 1080p streams every camera offers, and 1.5 times in Medium.
     */
    public static int sourceCropSize(int outputSize, boolean high) {
        return RoundSpec.sourceCropSize(outputSize, high);
    }

    /** Applied to the session builder after the stock settings (InstantCameraView2.showCamera). */
    @NonNull
    public static RoundVideoSession.Builder configure(@NonNull RoundVideoSession.Builder builder) {
        switch (NeoOptions.ROUND_START_CAMERA.get()) {
            case FRONT:
                builder.setInitialFacing(RoundVideoSession.CameraFacing.FRONT);
                break;
            case BACK:
                builder.setInitialFacing(RoundVideoSession.CameraFacing.BACK);
                break;
            default:
                break; // the stock "last used camera"
        }
        int size = outputResolution(SharedSettings.roundVideoOutputResolution.get()).getSize();
        boolean frameRate60 = SharedSettings.roundVideoFrameRate.get() == RoundVideoSession.FrameRate.FPS_60;
        if (frameRate60 && (NeoCameraPolicy.isConstrained() || Boolean.FALSE.equals(NeoRoundEncoder.supports(size, 60)))) {
            builder.setFrameRate(RoundVideoSession.FrameRate.FPS_30);
        }
        if (NeoCameraPolicy.isConstrained()
                && SharedSettings.roundVideoCameraResolution.get() == RoundVideoSession.CameraResolution.HIGH) {
            builder.setCameraResolution(RoundVideoSession.CameraResolution.MEDIUM);
        }
        return builder;
    }

    /** Initial value of RoundVideoCameraController.warmSwitchDisabled is the inverse of this. */
    public static boolean isFastSwitchAllowed() {
        return NeoCameraPolicy.isFastSwitchAllowed();
    }

    /**
     * RoundVideoCameraController.isPreferredCameraCandidate: when lenses are on and the back camera
     * reaches an ultra-wide, record with exactly the camera the lens catalog describes. Null keeps
     * the stock choice. Runs on the camera thread, so it may load the catalog.
     */
    @Nullable
    public static Boolean isPreferredCamera(@NonNull String candidateId, @NonNull String currentId, boolean back) {
        if (!back || !NeoOptions.ROUND_LENS_SWITCHER.value()) {
            return null;
        }
        NeoLensCatalog.BackCamera camera = NeoLensCatalog.get();
        if (camera == null || !camera.hasWide()) {
            return null;
        }
        if (camera.id.equals(candidateId)) {
            return true;
        }
        if (camera.id.equals(currentId)) {
            return false;
        }
        return null;
    }

    /** Initial base zoom ratio of the back camera of a new session: the chosen start lens. */
    public static float initialBackZoomRatio() {
        LensMath.Lens lens = startLens();
        return lens == null ? 1f : lens.zoomRatio;
    }

    /** The lens a session starts with, if the lens buttons are on and the catalog is loaded. */
    @Nullable
    static LensMath.Lens startLens() {
        if (!NeoOptions.ROUND_LENS_SWITCHER.value()) {
            return null;
        }
        NeoLensCatalog.BackCamera camera = NeoLensCatalog.peek();
        return camera == null ? null : camera.find(NeoOptions.ROUND_START_LENS.get());
    }

    /** Adds the lens button to the camera buttons of InstantCameraView2, after the flash button. */
    @NonNull
    public static NeoLensButton attachLensButton(@NonNull LinearLayout buttonsLayout, @NonNull FlashViews flashViews,
                                                 boolean isNewDesign, @Nullable Theme.ResourcesProvider resourcesProvider,
                                                 @NonNull NeoLensButton.ZoomSetter zoomSetter) {
        NeoLensButton button = new NeoLensButton(buttonsLayout.getContext(), zoomSetter);
        buttonsLayout.addView(button, LayoutHelper.createLinear(44, 44));
        if (!isNewDesign) {
            flashViews.add(button);
        } else if (resourcesProvider != null && !resourcesProvider.isDark()) {
            button.setInvert(0.6f);
        }
        return button;
    }
}
