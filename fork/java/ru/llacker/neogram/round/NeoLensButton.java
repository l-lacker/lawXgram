package ru.llacker.neogram.round;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.utils.recorder.round.RoundVideoSession;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.core.round.LensMath;

/**
 * Lens button of the video message camera: shows the current zoom-ratio lens of the back camera
 * ("0.6", "1×", "2×") and cycles through the lenses on tap. Hidden for the front camera, when the
 * back camera has a single lens and when the lens buttons are turned off.
 */
@SuppressLint("ViewConstructor")
public class NeoLensButton extends View implements FlashViews.Invertable {

    /** Applies the base zoom ratio to the current session; false when there is no session. */
    public interface ZoomSetter {
        boolean setBaseZoomRatio(float ratio);
    }

    private final ZoomSetter zoomSetter;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<LensMath.Lens> lenses = Collections.emptyList();
    private int selected = -1;
    private RoundVideoSession.CameraFacing facing;
    private boolean frameRate60;
    private boolean loadRequested;

    NeoLensButton(@NonNull Context context, @NonNull ZoomSetter zoomSetter) {
        super(context);
        this.zoomSetter = zoomSetter;
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.66f));
        fillPaint.setStyle(Paint.Style.FILL);
        setInvert(0f);
        setVisibility(GONE);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setOnClickListener(view -> next());
    }

    /** A new recording session starts: forget the lens of the previous one. */
    public void reset() {
        selected = -1;
        facing = null;
        frameRate60 = false;
        setVisibility(GONE);
    }

    /** The session reported its camera (InstantCameraView2.onCameraCapabilitiesChanged). */
    public void onCapabilities(@NonNull RoundVideoSession.CameraCapabilities capabilities) {
        facing = capabilities.getActiveFacing();
        frameRate60 = capabilities.getActiveFrameRate() == RoundVideoSession.FrameRate.FPS_60;
        refresh();
    }

    private void refresh() {
        if (!NeoOptions.ROUND_LENS_SWITCHER.value()) {
            setVisibility(GONE);
            return;
        }
        if (!NeoLensCatalog.isLoaded()) {
            setVisibility(GONE);
            if (!loadRequested) {
                loadRequested = true;
                Utilities.globalQueue.postRunnable(() -> {
                    NeoLensCatalog.get();
                    AndroidUtilities.runOnUIThread(this::refresh);
                });
            }
            return;
        }
        NeoLensCatalog.BackCamera camera = NeoLensCatalog.peek();
        // at 60 fps the stock may pick another back camera if this one cannot do 60
        if (camera == null || facing != RoundVideoSession.CameraFacing.BACK || frameRate60 && !camera.supports60Fps) {
            setVisibility(GONE);
            return;
        }
        lenses = camera.lenses;
        if (selected < 0 || selected >= lenses.size()) {
            LensMath.Lens start = NeoRoundCamera.startLens();
            LensMath.Lens main = camera.find(LensMath.Kind.MAIN);
            selected = Math.max(0, lenses.indexOf(start != null ? start : main));
            zoomSetter.setBaseZoomRatio(lenses.get(selected).zoomRatio);
        }
        updateDescription();
        setVisibility(VISIBLE);
        invalidate();
    }

    private void next() {
        if (lenses.size() < 2 || selected < 0) {
            return;
        }
        int next = (selected + 1) % lenses.size();
        if (!zoomSetter.setBaseZoomRatio(lenses.get(next).zoomRatio)) {
            return;
        }
        selected = next;
        updateDescription();
        invalidate();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        animate().cancel();
        setScaleX(0.86f);
        setScaleY(0.86f);
        animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    private void updateDescription() {
        if (selected < 0 || selected >= lenses.size()) {
            return;
        }
        NumberFormat format = NumberFormat.getNumberInstance(LocaleController.getInstance().getCurrentLocale());
        format.setMaximumFractionDigits(1);
        setContentDescription(LocaleController.formatString(R.string.NeoRoundLensDescription,
                format.format(lenses.get(selected).zoomRatio)));
    }

    @Override
    public void setInvert(float invert) {
        int color = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, invert);
        textPaint.setColor(color);
        ringPaint.setColor(color);
        fillPaint.setColor(ColorUtils.setAlphaComponent(color, 0x33));
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (selected < 0 || selected >= lenses.size()) {
            return;
        }
        LensMath.Lens lens = lenses.get(selected);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = dp(13);
        if (lens.kind != LensMath.Kind.MAIN) {
            canvas.drawCircle(cx, cy, radius, fillPaint);
        }
        canvas.drawCircle(cx, cy, radius, ringPaint);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        canvas.drawText(lens.label(), cx, cy - (metrics.ascent + metrics.descent) / 2f, textPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
    }
}
