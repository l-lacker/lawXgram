package ru.llacker.lawxgram.helpers;

import android.graphics.Bitmap;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.utils.BitmapsCache;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import app.nekogram.gifski.Gifski;

public class StickerHelper {
    private static final int MAX_RENDER_WORKERS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
    private static final ThreadPoolExecutor rendererExecutor = new ThreadPoolExecutor(
            MAX_RENDER_WORKERS,
            MAX_RENDER_WORKERS,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
    );

    static {
        rendererExecutor.allowCoreThreadTimeOut(true);
    }

    public static void convertStickerFormat(String path, boolean animated, Consumer<String> callback) {
        var resultPath = path + ".gif";
        rendererExecutor.execute(() -> {
            BitmapsCache.Cacheable source = null;
            try {
                var cacheOptions = new BitmapsCache.CacheOptions();
                int width;
                int height;
                if (animated) {
                    RLottieDrawable drawable = new RLottieDrawable(new File(path), null, 512, 512, cacheOptions, false, null, 0, false);
                    source = drawable;
                    width = drawable.getIntrinsicWidth();
                    height = drawable.getIntrinsicHeight();
                } else {
                    AnimatedFileDrawable drawable = new AnimatedFileDrawable(new File(path), true, 0, 0, null, null, null, 0, 0, false, 0, 0, cacheOptions);
                    source = drawable;
                    width = drawable.getIntrinsicWidth();
                    height = drawable.getIntrinsicHeight();
                }
                var success = renderToGif(resultPath, source, width, height);
                if (success) {
                    callback.accept(resultPath);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (source instanceof RLottieDrawable) {
                    ((RLottieDrawable) source).recycle(false);
                } else if (source instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) source).recycle();
                }
            }
        });
    }

    private static boolean renderToGif(String path, BitmapsCache.Cacheable source, int width, int height) {
        Bitmap bitmap = null;
        Gifski gifski = null;
        boolean gifskiFinished = false;
        try {
            var fps = source.getFps();
            FileLog.d("start gif rendering for path = " + path + ", width = " + width + ", height = " + height + ", fps = " + fps);
            source.prepareForGenerateCache();
            var settings = new Gifski.Settings();
            settings.setHeight(height);
            settings.setWidth(width);
            settings.setQuality(90);
            settings.setRepeat((short) 0);
            gifski = new Gifski(settings);
            gifski.setFileOutput(path);
            var framePosition = 0;
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            while (source.getNextFrame(bitmap) == 1) {
                var pts = (double) framePosition / fps;
                gifski.addFrameBitmap(framePosition, bitmap, pts);
                framePosition++;
            }
            gifski.finish();
            gifskiFinished = true;
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (gifski != null && !gifskiFinished) {
                try {
                    gifski.finish();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            source.releaseForGenerateCache();
        }
        return false;
    }
}
