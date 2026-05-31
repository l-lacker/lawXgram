package ru.llacker.lawxgram.settings;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import ru.llacker.lawxgram.LawxConfig;
import ru.llacker.lawxgram.helpers.RoundVideoQualityHelper;

public class LawxVideoMessagesSettingsActivity extends BaseLawxSettingsActivity {

    private final int resolutionRow = rowId++;
    private final int fpsRow = rowId++;
    private final int bitrateRow = rowId++;
    private final int smoothCameraSwitchRow = rowId++;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int minSide = RoundVideoQualityHelper.getTelegramStandardSide(currentAccount);

        items.add(UItem.asHeader(LocaleController.getString(R.string.VideoMessagesQualityResolution)));
        items.add(createResolutionItem(minSide));
        items.add(UItem.asShadow(LocaleController.formatString(R.string.VideoMessagesQualityResolutionAbout, minSide, RoundVideoQualityHelper.TELEGRAM_MAX_SIDE)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.VideoMessagesQualityFps)));
        items.add(UItem.asSlideView(new String[] {
                LocaleController.getString(R.string.VideoMessagesQualityFps30),
                LocaleController.getString(R.string.VideoMessagesQualityFps60)
        }, LawxConfig.roundVideoFpsCap >= 60 ? 1 : 0, index -> LawxConfig.setRoundVideoFpsCap(index == 1 ? 60 : 30)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.VideoMessagesQualityFpsAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.VideoMessagesQualityBitrate)));
        UItem bitrateItem = UItem.asIntSlideView(
                0,
                RoundVideoQualityHelper.MIN_BITRATE_MBPS,
                LawxConfig.roundVideoBitrateMbps,
                RoundVideoQualityHelper.MAX_BITRATE_MBPS,
                value -> value + " Mbit/s",
                LawxConfig::setRoundVideoBitrateMbps
        );
        bitrateItem.id = bitrateRow;
        items.add(bitrateItem);
        items.add(UItem.asShadow(getBitrateSummary()));

        items.add(UItem.asHeader(LocaleController.getString(R.string.VideoMessagesQualityCamera)));
        items.add(UItem.asCheck(smoothCameraSwitchRow, LocaleController.getString(R.string.VideoMessagesQualitySmoothCameraSwitch)).slug("smoothCameraSwitch").setChecked(LawxConfig.roundVideoSmoothCameraSwitch));
        items.add(UItem.asShadow(LocaleController.getString(R.string.VideoMessagesQualityCameraAbout)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == smoothCameraSwitchRow) {
            LawxConfig.toggleRoundVideoSmoothCameraSwitch();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.roundVideoSmoothCameraSwitch);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.VideoMessagesQuality);
    }

    @Override
    protected String getKey() {
        return "c";
    }

    private UItem createResolutionItem(int minSide) {
        int value = RoundVideoQualityHelper.clampConfiguredSide(currentAccount, LawxConfig.roundVideoResolution);
        int count = (RoundVideoQualityHelper.TELEGRAM_MAX_SIDE - minSide) / 16 + 1;
        int[] steps = new int[count];
        for (int i = 0; i < count; i++) {
            steps[i] = minSide + i * 16;
        }
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_INTSLIDE, false);
        item.id = resolutionRow;
        item.intValue = value;
        item.object = SlideIntChooseView.Options.make(0, steps, 1, (type, val) -> val + "x" + val);
        item.intCallback = LawxConfig::setRoundVideoResolution;
        return item;
    }

    private CharSequence getBitrateSummary() {
        StringBuilder builder = new StringBuilder(LocaleController.getString(R.string.VideoMessagesQualityBitrateAbout));
        builder.append('\n');
        for (int mbps = RoundVideoQualityHelper.MIN_BITRATE_MBPS; mbps <= RoundVideoQualityHelper.MAX_BITRATE_MBPS; mbps++) {
            long size = RoundVideoQualityHelper.estimateRoundVideoSize(currentAccount, mbps * 1_000_000, RoundVideoQualityHelper.MAX_RECORDING_DURATION_MS);
            if (mbps > RoundVideoQualityHelper.MIN_BITRATE_MBPS) {
                builder.append("  ");
            }
            builder.append(mbps).append(": ").append(RoundVideoQualityHelper.formatSizeMb(size));
        }
        return builder;
    }
}
