package ru.llacker.neogram.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.utils.recorder.audio.AudioRecordingSession;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.llacker.neogram.NeoBrand;
import ru.llacker.neogram.config.NeoConfig;
import ru.llacker.neogram.round.NeoCameraPolicy;

/**
 * Backup of Neogram settings as a JSON file: Neogram's own exportable options plus Telegram's
 * round-video settings that the Neogram pages edit. Device-specific state (failure counters,
 * update bookkeeping) is not exportable and never leaves the device.
 */
public final class NeoBackup {

    public static final String MIME = "application/json";
    /** Larger files are not settings backups. */
    private static final int MAXIMUM_SIZE = 1024 * 1024;

    /** What an import changed. */
    public static final class Result {
        public boolean invalid;
        public int applied;
        public int skipped;
    }

    private NeoBackup() {
    }

    @NonNull
    public static String fileName() {
        return NeoBrand.appName() + "-settings-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
    }

    @NonNull
    public static JSONObject build() {
        JSONObject root = NeoConfig.exportJson();
        try {
            root.put("app", NeoBrand.appName());
            JSONObject shared = new JSONObject();
            shared.put("round_recorder", NeoCameraPolicy.recorderMode().name());
            shared.put("round_video_output_resolution", SharedSettings.roundVideoOutputResolution.get().name());
            shared.put("round_video_camera_resolution", SharedSettings.roundVideoCameraResolution.get().name());
            shared.put("round_video_frame_rate", SharedSettings.roundVideoFrameRate.get().name());
            shared.put("round_video_video_bitrate", SharedSettings.roundVideoVideoBitrate.get());
            shared.put("round_video_composition", SharedSettings.roundVideoComposition.get());
            if (SharedSettings.audioRecorderV2Enabled.isSet()) {
                shared.put("audio_recorder_v2_enabled", SharedSettings.audioRecorderV2Enabled.get());
            }
            shared.put("audio_recorder_noise_suppression", SharedSettings.audioRecorderNoiseSuppression.get().name());
            root.put("shared", shared);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    @NonNull
    public static Result restore(@NonNull JSONObject root) {
        NeoConfig.ImportResult options = NeoConfig.importJson(root);
        Result result = new Result();
        result.invalid = options.invalidFormat;
        if (result.invalid) {
            return result;
        }
        result.applied = options.applied;
        result.skipped = options.skipped.size();
        JSONObject shared = root.optJSONObject("shared");
        if (shared != null) {
            count(result, applyEnum(shared, "round_recorder", NeoCameraPolicy.RecorderMode.class, NeoCameraPolicy::setRecorderMode));
            count(result, applyEnum(shared, "round_video_output_resolution", RoundVideoSession.OutputResolution.class,
                    SharedSettings.roundVideoOutputResolution::set));
            count(result, applyEnum(shared, "round_video_camera_resolution", RoundVideoSession.CameraResolution.class,
                    SharedSettings.roundVideoCameraResolution::set));
            count(result, applyEnum(shared, "round_video_frame_rate", RoundVideoSession.FrameRate.class,
                    SharedSettings.roundVideoFrameRate::set));
            if (shared.has("round_video_video_bitrate")) {
                int bitrate = shared.optInt("round_video_video_bitrate", -1);
                if (bitrate >= 100_000 && bitrate <= 10_000_000) {
                    SharedSettings.roundVideoVideoBitrate.set(bitrate);
                    result.applied++;
                } else {
                    result.skipped++;
                }
            }
            if (shared.has("round_video_composition")) {
                Object value = shared.opt("round_video_composition");
                if (value instanceof Boolean) {
                    SharedSettings.roundVideoComposition.set((Boolean) value);
                    result.applied++;
                } else {
                    result.skipped++;
                }
            }
            if (shared.has("audio_recorder_v2_enabled")) {
                Object value = shared.opt("audio_recorder_v2_enabled");
                if (value instanceof Boolean) {
                    SharedSettings.audioRecorderV2Enabled.set((Boolean) value);
                    result.applied++;
                } else {
                    result.skipped++;
                }
            }
            count(result, applyEnum(shared, "audio_recorder_noise_suppression", AudioRecordingSession.NoiseSuppression.class,
                    SharedSettings.audioRecorderNoiseSuppression::set));
        }
        return result;
    }

    private interface Setter<E> {
        void set(@NonNull E value);
    }

    /** True if applied, false if present but invalid, null if absent. */
    @Nullable
    private static <E extends Enum<E>> Boolean applyEnum(@NonNull JSONObject shared, @NonNull String key,
                                                         @NonNull Class<E> type, @NonNull Setter<E> setter) {
        if (!shared.has(key)) {
            return null;
        }
        try {
            setter.set(Enum.valueOf(type, shared.optString(key)));
            return true;
        } catch (IllegalArgumentException e) {
            return false; // an option of a newer or patched build
        }
    }

    private static void count(@NonNull Result result, @Nullable Boolean applied) {
        if (applied == null) {
            return;
        }
        if (applied) {
            result.applied++;
        } else {
            result.skipped++;
        }
    }

    // ---- files ----------------------------------------------------------------------------------

    @WorkerThread
    public static void write(@NonNull Context context, @NonNull Uri uri) throws Exception {
        byte[] data = build().toString(2).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IllegalStateException("cannot open " + uri);
            }
            out.write(data);
        }
    }

    @WorkerThread
    @NonNull
    public static JSONObject read(@NonNull Context context, @NonNull Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("cannot open " + uri);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAXIMUM_SIZE) {
                    throw new IllegalStateException("not a settings backup: too large");
                }
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }

    /** Sends the backup as a file to Saved Messages of the current account. */
    @WorkerThread
    public static void sendToSavedMessages(int account) throws Exception {
        File directory = new File(AndroidUtilities.getCacheDir(), "neogram");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create " + directory);
        }
        File file = new File(directory, fileName());
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(build().toString(2).getBytes(StandardCharsets.UTF_8));
        }
        long self = UserConfig.getInstance(account).getClientUserId();
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account),
                file.getAbsolutePath(), file.getAbsolutePath(), null, null, MIME, self, null, null, null, null, null,
                true, 0, null, null, false));
    }

    static void log(@NonNull Exception e) {
        FileLog.e(e);
    }
}
