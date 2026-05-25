package ru.llacker.lawxgram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.View;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class QrView extends View {

    private static final int CROSSFADE_WIDTH_DP = 140;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat contentBitmapAlpha = new AnimatedFloat(1f, this, 0, 2000, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final Paint crossfadeFromPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
        setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(CROSSFADE_WIDTH_DP), new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }};
    private final Paint crossfadeToPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
        setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(CROSSFADE_WIDTH_DP), new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }};
    private RLottieDrawable loadingMatrix;
    private Bitmap contentBitmap, oldContentBitmap, qrLogo;
    private volatile String link;
    private final float[] radii = new float[8];
    private final AtomicInteger prepareGeneration = new AtomicInteger();

    public QrView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            recycleQrLogo();
            final int generation = prepareGeneration.incrementAndGet();
            Utilities.themeQueue.postRunnable(() -> prepareContent(w, h, generation));
        }
    }

    private void drawLoading(Canvas canvas, int multiple, int size, float scale) {
        if (loadingMatrix == null) {
            loadingMatrix = new RLottieDrawable(R.raw.qr_matrix, "qr_matrix", AndroidUtilities.dp(200), AndroidUtilities.dp(200));
            loadingMatrix.setMasterParent(this);
            loadingMatrix.setAutoRepeat(1);
            loadingMatrix.setColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY);
            loadingMatrix.start();
        }
        int width = getWidth();
        loadingMatrix.setBounds(16, 16, width - 16, width - 16);
        loadingMatrix.draw(canvas);
        int imageBloks = Math.round((size - 32) / 4.65f / multiple);
        if (imageBloks % 2 != 37 % 2) {
            imageBloks++;
        }
        int imageSize = imageBloks * multiple - 24;
        int imageX = (size - imageSize) / 2;
        canvas.save();
        canvas.scale(scale, scale);
        paint.setColor(Color.BLACK);
        QRCodeWriter.drawSideQuads(canvas, 0, 0, paint, 7, multiple, 16, size, .75f, radii, true);
        if (qrLogo == null) {
            String svg = AndroidUtilities.readRes(null, R.raw.qr_logo);
            qrLogo = SvgHelper.getBitmap(svg, imageSize, imageSize, false);
        }
        canvas.drawBitmap(qrLogo, imageX, imageX, null);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        int width = getWidth();
        int qrSize = 37;
        int multiple = getWidth() / qrSize;
        int size = multiple * qrSize + 32;
        float scale = width / (float) size;

        float crossfadeAlpha = contentBitmapAlpha.set(1f);
        boolean crossfading = crossfadeAlpha > 0 && crossfadeAlpha < 1;
        if (!crossfading && crossfadeAlpha >= 1f && oldContentBitmap != null) {
            AndroidUtilities.recycleBitmap(oldContentBitmap);
            oldContentBitmap = null;
        }

        if (crossfadeAlpha < 1f) {
            if (crossfading) {
                AndroidUtilities.rectTmp.set(0, 0, size, size);
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255, Canvas.ALL_SAVE_FLAG);
            }
            if (oldContentBitmap != null) {
                canvas.save();
                canvas.scale(scale, scale);
                canvas.drawBitmap(oldContentBitmap, 0, 0, null);
                canvas.restore();
            } else {
                drawLoading(canvas, multiple, size, scale);
            }
            if (crossfading) {
                float h = AndroidUtilities.dp(CROSSFADE_WIDTH_DP);
                canvas.save();
                canvas.translate(0, -h + (size + h) * (1f - crossfadeAlpha));
                canvas.drawRect(0, 0, size, size + h, crossfadeToPaint);
                canvas.restore();
                canvas.restore();
            }
        }
        if (crossfadeAlpha > 0f) {
            if (crossfading) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255, Canvas.ALL_SAVE_FLAG);
            }
            if (contentBitmap != null) {
                canvas.save();
                canvas.scale(scale, scale);
                canvas.drawBitmap(contentBitmap, 0f, 0f, null);
                canvas.restore();
            } else {
                drawLoading(canvas, multiple, size, scale);
            }
            if (crossfading) {
                float h = AndroidUtilities.dp(CROSSFADE_WIDTH_DP);
                canvas.save();
                canvas.translate(0, -h + (getHeight() + h) * (1f - crossfadeAlpha));
                canvas.drawRect(0, -h - getHeight(), getWidth(), getHeight() + h, crossfadeFromPaint);
                canvas.restore();
                canvas.restore();
            }
        }
    }

    public void clear() {
        prepareGeneration.incrementAndGet();
        link = null;
        resetPreparedState();
        recycleContentBitmaps();
        invalidate();
    }

    public void setData(String link) {
        this.link = link;
        final int w = getWidth(), h = getHeight();
        final int generation = prepareGeneration.incrementAndGet();
        Utilities.themeQueue.postRunnable(() -> prepareContent(w, h, generation));
        invalidate();
    }

    private volatile int hadWidth = -1, hadHeight = -1;
    private volatile String hadLink;
    private boolean firstPrepare = true;

    private void prepareContent(int w, int h, int generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        if (w == 0 || h == 0) {
            return;
        }
        final String currentLink = link;
        if (TextUtils.isEmpty(currentLink)) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!isCurrentGeneration(generation)) {
                    return;
                }
                firstPrepare = false;
                if (contentBitmap != null) {
                    Bitmap oldBitmap = contentBitmap;
                    contentBitmap = null;
                    contentBitmapAlpha.set(0, true);
                    if (oldContentBitmap != null) {
                        AndroidUtilities.recycleBitmap(oldContentBitmap);
                    }
                    oldContentBitmap = oldBitmap;
                    this.invalidate();
                } else if (oldContentBitmap != null) {
                    AndroidUtilities.recycleBitmap(oldContentBitmap);
                    oldContentBitmap = null;
                }
            });
            return;
        }

        final int currentHadWidth = hadWidth;
        final int currentHadHeight = hadHeight;
        final String currentHadLink = hadLink;
        if (TextUtils.equals(currentLink, currentHadLink) && currentHadWidth == w && currentHadHeight == h) {
            return;
        }

        if (!isCurrentGeneration(generation)) {
            return;
        }
        Bitmap qrBitmap = null;
        HashMap<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        QRCodeWriter writer = new QRCodeWriter();
        try {
            qrBitmap = writer.encode(currentLink, w, h, hints, null, 0.75f, 0, Color.BLACK);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (qrBitmap == null) {
            return;
        }

        Bitmap bitmap = qrBitmap;

        AndroidUtilities.runOnUIThread(() -> {
            if (!isCurrentGeneration(generation)) {
                AndroidUtilities.recycleBitmap(bitmap);
                return;
            }
            hadWidth = w;
            hadHeight = h;
            hadLink = currentLink;
            Bitmap oldBitmap = contentBitmap;
            contentBitmap = bitmap;
            if (!firstPrepare) {
                contentBitmapAlpha.set(0, true);
            }
            firstPrepare = false;
            if (oldContentBitmap != null) {
                AndroidUtilities.recycleBitmap(oldContentBitmap);
            }
            oldContentBitmap = oldBitmap;

            this.invalidate();
        });
    }

    private boolean isCurrentGeneration(int generation) {
        return generation == prepareGeneration.get();
    }

    private void recycleContentBitmaps() {
        if (contentBitmap != null) {
            AndroidUtilities.recycleBitmap(contentBitmap);
            contentBitmap = null;
        }
        if (oldContentBitmap != null) {
            AndroidUtilities.recycleBitmap(oldContentBitmap);
            oldContentBitmap = null;
        }
    }

    private void resetPreparedState() {
        hadWidth = -1;
        hadHeight = -1;
        hadLink = null;
        firstPrepare = true;
    }

    private void recycleQrLogo() {
        if (qrLogo != null) {
            AndroidUtilities.recycleBitmap(qrLogo);
            qrLogo = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        prepareGeneration.incrementAndGet();
        if (loadingMatrix != null) {
            loadingMatrix.stop();
            loadingMatrix.recycle(false);
            loadingMatrix = null;
        }
        recycleQrLogo();
        recycleContentBitmaps();
        resetPreparedState();
    }
}
