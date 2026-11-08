package ru.llacker.neogram.chat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/** Reads a QR code from a photo with zxing, which Telegram already bundles for its QR scanner. */
final class NeoQr {

    /** Longest side the photo is scaled down to: plenty for a code, bounded memory. */
    private static final int MAXIMUM_SIDE = 1600;

    private NeoQr() {
    }

    /** The text of the first QR code found, or null. Runs on a background thread. */
    @Nullable
    static String decode(@NonNull File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > MAXIMUM_SIDE) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            String text = read(source);
            // a light code on a dark background
            return text != null ? text : read(source.invert());
        } finally {
            bitmap.recycle();
        }
    }

    @Nullable
    private static String read(@NonNull LuminanceSource source) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        for (Binarizer binarizer : new Binarizer[]{new HybridBinarizer(source), new GlobalHistogramBinarizer(source)}) {
            try {
                Result result = new QRCodeReader().decode(new BinaryBitmap(binarizer), hints);
                if (result != null && !TextUtils.isEmpty(result.getText())) {
                    return result.getText();
                }
            } catch (ReaderException ignored) {
                // no code with this binarizer
            }
        }
        return null;
    }
}
