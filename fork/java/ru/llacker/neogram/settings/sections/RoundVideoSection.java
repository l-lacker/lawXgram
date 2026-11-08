package ru.llacker.neogram.settings.sections;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.config.NeoOptions;
import ru.llacker.neogram.config.NeoValue;
import ru.llacker.neogram.core.round.LensMath;
import ru.llacker.neogram.core.round.RoundSpec;
import ru.llacker.neogram.round.NeoCameraPolicy;
import ru.llacker.neogram.round.NeoLensCatalog;
import ru.llacker.neogram.settings.NeoSettingsRow;
import ru.llacker.neogram.settings.NeoSettingsSection;

/**
 * "Video messages" page (opened from Media). Recorder, camera and lens options are Neogram's;
 * quality options are Telegram's own round-video settings ({@code SharedSettings}), edited in place.
 */
public final class RoundVideoSection {

    public static final NeoSettingsSection SECTION = new NeoSettingsSection("round_video", R.string.NeoRoundVideo,
            R.string.NeoRoundVideoInfo, R.drawable.neo_section_media, IconBackgroundColors.RED, RoundVideoSection::rows);

    /** Bitrates offered by the stock round-video settings. */
    private static final int[] BITRATES = {750_000, 1_000_000, 1_200_000, 2_000_000};

    private RoundVideoSection() {
    }

    private static void rows(@NonNull List<NeoSettingsRow> rows) {
        rows.add(NeoSettingsRow.header(R.string.NeoRoundRecording));
        rows.add(NeoSettingsRow.choice("recorder",
                        NeoValue.of(NeoCameraPolicy::recorderMode, NeoCameraPolicy::setRecorderMode),
                        R.string.NeoRoundRecorder, R.string.NeoRoundRecorderInfo)
                .option(NeoCameraPolicy.RecorderMode.AUTO, R.string.NeoRoundRecorderAuto)
                .option(NeoCameraPolicy.RecorderMode.NEW, R.string.NeoRoundRecorderNew)
                .option(NeoCameraPolicy.RecorderMode.CLASSIC, R.string.NeoRoundRecorderClassic)
                .build()
                .describedBy(RoundVideoSection::describeRecorder));
        rows.add(NeoSettingsRow.choice("start_camera", NeoOptions.ROUND_START_CAMERA,
                        R.string.NeoRoundStartCamera, R.string.NeoRoundStartCameraInfo)
                .option(NeoOptions.RoundCamera.LAST, R.string.NeoRoundStartCameraLast)
                .option(NeoOptions.RoundCamera.FRONT, R.string.NeoRoundStartCameraFront)
                .option(NeoOptions.RoundCamera.BACK, R.string.NeoRoundStartCameraBack)
                .build()
                .onlyIf(RoundVideoSection::isNewRecorder));
        rows.add(NeoSettingsRow.choice("fast_switch", NeoOptions.ROUND_FAST_SWITCH,
                        R.string.NeoRoundFastSwitch, R.string.NeoRoundFastSwitchInfo)
                .option(NeoOptions.FastCameraSwitch.AUTO, R.string.NeoRoundFastSwitchAuto)
                .option(NeoOptions.FastCameraSwitch.OFF, R.string.NeoRoundFastSwitchOff)
                .build()
                .onlyIf(() -> isNewRecorder() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R));

        rows.add(NeoSettingsRow.header(R.string.NeoRoundLenses)
                .onlyIf(RoundVideoSection::isNewRecorder));
        rows.add(NeoSettingsRow.toggle("lens_button", NeoOptions.ROUND_LENS_SWITCHER,
                        R.string.NeoRoundLensButton, R.string.NeoRoundLensButtonInfo)
                .onlyIf(() -> isNewRecorder() && hasLenses()));
        NeoSettingsRow.ChoiceBuilder<LensMath.Kind> startLens = NeoSettingsRow.choice("start_lens",
                NeoOptions.ROUND_START_LENS, R.string.NeoRoundStartLens, R.string.NeoRoundStartLensInfo);
        NeoLensCatalog.BackCamera camera = lensCamera();
        if (camera != null) {
            for (LensMath.Lens lens : camera.lenses) {
                startLens.option(lens.kind, NeoStrings.get(lensName(lens.kind)) + " (" + lens.label() + ")");
            }
        } else {
            startLens.option(LensMath.Kind.MAIN, R.string.NeoRoundLensMain);
        }
        rows.add(startLens.build()
                .onlyIf(() -> isNewRecorder() && hasLenses() && NeoOptions.ROUND_LENS_SWITCHER.value()));
        rows.add(NeoSettingsRow.info(R.string.NeoRoundNoLenses)
                .onlyIf(() -> isNewRecorder() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasLenses()));
        rows.add(NeoSettingsRow.info(R.string.NeoRoundLensesNeedAndroid11)
                .onlyIf(() -> isNewRecorder() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R));

        rows.add(NeoSettingsRow.header(R.string.NeoRoundQuality)
                .onlyIf(RoundVideoSection::isNewRecorder));
        NeoSettingsRow.ChoiceBuilder<RoundVideoSession.OutputResolution> resolution = NeoSettingsRow.choice("resolution",
                NeoValue.of(SharedSettings.roundVideoOutputResolution::get, SharedSettings.roundVideoOutputResolution::set),
                R.string.NeoRoundResolution, R.string.NeoRoundResolutionInfo);
        List<RoundVideoSession.OutputResolution> resolutions = new ArrayList<>(Arrays.asList(RoundVideoSession.OutputResolution.values()));
        Collections.sort(resolutions, (a, b) -> Integer.compare(a.getSize(), b.getSize()));
        for (RoundVideoSession.OutputResolution value : resolutions) {
            resolution.option(value, value.getSize() + "×" + value.getSize());
        }
        rows.add(resolution.build().onlyIf(RoundVideoSection::isNewRecorder));
        rows.add(NeoSettingsRow.choice("camera_detail",
                        NeoValue.of(SharedSettings.roundVideoCameraResolution::get, SharedSettings.roundVideoCameraResolution::set),
                        R.string.NeoRoundCameraMode, R.string.NeoRoundCameraModeInfo)
                .option(RoundVideoSession.CameraResolution.HIGH, R.string.NeoRoundCameraModeHigh)
                .option(RoundVideoSession.CameraResolution.MEDIUM, R.string.NeoRoundCameraModeMedium)
                .option(RoundVideoSession.CameraResolution.LOW, R.string.NeoRoundCameraModeLow)
                .build()
                .onlyIf(RoundVideoSection::isNewRecorder));
        NeoSettingsRow.ChoiceBuilder<RoundVideoSession.FrameRate> frameRate = NeoSettingsRow.choice("frame_rate",
                NeoValue.of(SharedSettings.roundVideoFrameRate::get, SharedSettings.roundVideoFrameRate::set),
                R.string.NeoRoundFrameRate, R.string.NeoRoundFrameRateInfo);
        for (RoundVideoSession.FrameRate value : RoundVideoSession.FrameRate.values()) {
            frameRate.option(value, LocaleController.formatString(R.string.NeoRoundFrameRateValue, value.getValue()));
        }
        rows.add(frameRate.build().onlyIf(RoundVideoSection::isNewRecorder));
        NeoSettingsRow.ChoiceBuilder<Integer> bitrate = NeoSettingsRow.choice("bitrate",
                NeoValue.of(SharedSettings.roundVideoVideoBitrate::get, SharedSettings.roundVideoVideoBitrate::set),
                R.string.NeoRoundBitrate, 0);
        for (int value : BITRATES) {
            bitrate.option(value, formatBitrate(value));
        }
        rows.add(bitrate.build()
                .describedBy(RoundVideoSection::describeBitrate)
                .onlyIf(RoundVideoSection::isNewRecorder));
        rows.add(NeoSettingsRow.toggle("styling",
                        NeoValue.of(SharedSettings.roundVideoComposition::get, SharedSettings.roundVideoComposition::set),
                        R.string.NeoRoundStyling, R.string.NeoRoundStylingInfo)
                .onlyIf(RoundVideoSection::isNewRecorder));
    }

    private static boolean isNewRecorder() {
        return NeoCameraPolicy.useNewRecorder(UserConfig.selectedAccount);
    }

    /** The lens catalog; read here on first use (a few camera-service calls on this thread). */
    @Nullable
    private static NeoLensCatalog.BackCamera lensCamera() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? NeoLensCatalog.get() : null;
    }

    private static boolean hasLenses() {
        return lensCamera() != null;
    }

    private static int lensName(@NonNull LensMath.Kind kind) {
        switch (kind) {
            case WIDE:
                return R.string.NeoRoundLensWide;
            case TELE:
                return R.string.NeoRoundLensTele;
            default:
                return R.string.NeoRoundLensMain;
        }
    }

    @Nullable
    private static CharSequence describeRecorder() {
        if (NeoCameraPolicy.recorderMode() != NeoCameraPolicy.RecorderMode.AUTO) {
            return null;
        }
        NeoCameraPolicy.AutoFallback fallback = NeoCameraPolicy.autoFallback(UserConfig.selectedAccount);
        if (fallback == null) {
            return NeoStrings.get(R.string.NeoRoundRecorderAutoNew);
        }
        switch (fallback) {
            case SERVER:
                return NeoStrings.get(R.string.NeoRoundRecorderAutoServer);
            case LEGACY_CAMERA:
                return NeoStrings.get(R.string.NeoRoundRecorderAutoLegacy);
            default:
                return NeoStrings.get(R.string.NeoRoundRecorderAutoFailures);
        }
    }

    @NonNull
    private static CharSequence describeBitrate() {
        long bytes = RoundSpec.estimatedBytes(SharedSettings.roundVideoVideoBitrate.get(), RoundSpec.AUDIO_BITRATE,
                RoundSpec.MAXIMUM_DURATION_MS);
        return LocaleController.formatString(R.string.NeoRoundBitrateInfo, AndroidUtilities.formatFileSize(bytes));
    }

    /** "0.75", "1", "1.2" Mbit/s in the app language. */
    @NonNull
    private static String formatBitrate(int bitrate) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        NumberFormat format = NumberFormat.getNumberInstance(locale != null ? locale : Locale.US);
        format.setMaximumFractionDigits(2);
        return LocaleController.formatString(R.string.NeoRoundBitrateValue, format.format(bitrate / 1_000_000.0));
    }
}
