package ru.llacker.neogram.round;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

/**
 * What the AVC encoder of the round-video recorder can do on this device.
 *
 * <p>The stock recorder takes the first AVC encoder ({@code MediaCodec.createEncoderByType}, i.e.
 * the first one in the codec list) and fails the whole recording when it cannot encode the chosen
 * size and frame rate. Encoders of some MediaTek and Allwinner chips need sizes aligned to 16, so
 * 360×360 is rejected there; low-end ones lack 60 fps. Knowing this up front lets the session pick
 * a size and frame rate the encoder accepts instead of failing.</p>
 *
 * <p>Reading the codec list is slow the first time, so it is loaded in the background when a chat
 * prepares the recorder; until then nothing is changed.</p>
 */
final class NeoRoundEncoder {

    private static final Object NONE = new Object();
    private static volatile Object capabilities;

    private NeoRoundEncoder() {
    }

    static void warmUp() {
        if (capabilities == null) {
            Utilities.globalQueue.postRunnable(NeoRoundEncoder::load);
        }
    }

    /** Whether the encoder accepts a square of this size at this frame rate; null while unknown. */
    @Nullable
    static Boolean supports(int size, int frameRate) {
        Object value = capabilities;
        if (!(value instanceof MediaCodecInfo.VideoCapabilities)) {
            return null;
        }
        MediaCodecInfo.VideoCapabilities video = (MediaCodecInfo.VideoCapabilities) value;
        try {
            return video.areSizeAndRateSupported(size, size, frameRate);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void load() {
        if (capabilities != null) {
            return;
        }
        Object result = NONE;
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.isEncoder() || !supportsAvc(info)) {
                    continue;
                }
                MediaCodecInfo.VideoCapabilities video = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).getVideoCapabilities();
                if (video != null) {
                    result = video;
                    FileLog.d("neo camera: AVC encoder " + info.getName() + ", alignment "
                            + video.getWidthAlignment() + "x" + video.getHeightAlignment());
                }
                break;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        capabilities = result;
    }

    private static boolean supportsAvc(MediaCodecInfo info) {
        for (String type : info.getSupportedTypes()) {
            if (MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }
}
