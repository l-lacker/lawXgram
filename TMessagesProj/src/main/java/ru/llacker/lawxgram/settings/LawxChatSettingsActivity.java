package ru.llacker.lawxgram.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import ru.llacker.lawxgram.LawxConfig;
import ru.llacker.lawxgram.helpers.EntitiesHelper;
import ru.llacker.lawxgram.helpers.PopupHelper;
import ru.llacker.lawxgram.helpers.VoiceEnhancementsHelper;
import ru.llacker.lawxgram.helpers.WhisperHelper;

public class LawxChatSettingsActivity extends BaseLawxSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem resetItem;
    private ValueAnimator resetAnimator;

    private final int stickerSizeRow = rowId++;
    private final int hideTimeOnStickerRow = rowId++;
    private final int showTimeHintRow = rowId++;
    private final int reducedColorsRow = rowId++;

    private final int ignoreBlockedRow = rowId++;
    private final int quickForwardRow = rowId++;
    private final int hideKeyboardOnChatScrollRow = rowId++;
    private final int inlineBotsRow = rowId++;
    private final int showInlineBotManageButtonRow = rowId++;
    private final int tryToOpenAllLinksInIVRow = rowId++;
    private final int disableJumpToNextRow = rowId++;
    private final int disableGreetingStickerRow = rowId++;
    private final int hideChannelBottomButtonsRow = rowId++;
    private final int doubleTapActionRow = rowId++;
    private final int maxRecentStickersRow = rowId++;

    private final int transcribeProviderRow = rowId++;
    private final int cfCredentialsRow = rowId++;

    private final int markdownEnableRow = rowId++;
    private final int markdownParserRow = rowId++;
    private final int markdownParseLinksRow = rowId++;
    private final int markdown2Row = rowId++;

    private final int voiceEnhancementsRow = rowId++;
    private final int rearVideoMessagesRow = rowId++;
    private final int confirmAVRow = rowId++;
    private final int disableProximityEventsRow = rowId++;
    private final int disableVoiceMessageAutoPlayRow = rowId++;
    private final int unmuteVideosWithVolumeButtonsRow = rowId++;
    private final int autoPauseVideoRow = rowId++;
    private final int preferOriginalQualityRow = rowId++;

    private final int messageMenuRow = 100;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        return true;
    }

    @Override
    public View createView(Context context) {
        var fragmentView = super.createView(context);

        var menu = actionBar.createMenu();
        resetItem = menu.addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(LocaleController.getString(R.string.ResetStickerSize));
        resetItem.setTag(null);
        resetItem.setOnClickListener(v -> {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
            var item = listView.findItemByItemId(stickerSizeRow);
            var stickerCell = (StickerSizeCell) listView.findViewByItemId(stickerSizeRow);
            if (stickerCell != null) {
                if (resetAnimator != null) {
                    resetAnimator.cancel();
                }
                resetAnimator = ValueAnimator.ofFloat(LawxConfig.stickerSize, 14.0f);
                resetAnimator.setDuration(150);
                resetAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                resetAnimator.addUpdateListener(valueAnimator -> {
                    var floatValue = (float) valueAnimator.getAnimatedValue();
                    LawxConfig.setStickerSizeInMemory(floatValue);
                    stickerCell.setValue(floatValue);
                });
                resetAnimator.addListener(new AnimatorListenerAdapter() {
                    private boolean finished;

                    private void finish(Animator animation) {
                        if (finished) {
                            return;
                        }
                        finished = true;
                        LawxConfig.setStickerSizeInMemory(14.0f);
                        stickerCell.setValue(14.0f);
                        if (resetAnimator == animation) {
                            resetAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        finish(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish(animation);
                    }
                });
                resetAnimator.start();
            } else {
                LawxConfig.setStickerSizeInMemory(14.0f);
            }
            LawxConfig.setStickerSize(14.0f);
            if (item != null) {
                item.floatValue = 14.0f;
            }
        });
        AndroidUtilities.updateViewVisibilityAnimated(resetItem, Float.compare(LawxConfig.stickerSize, 14.0f) != 0, 1f, false);

        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (resetAnimator != null) {
            resetAnimator.cancel();
            resetAnimator = null;
        }
        super.onFragmentDestroy();
    }

    public String getDoubleTapActionText(int action) {
        return switch (action) {
            case LawxConfig.DOUBLE_TAP_ACTION_REACTION ->
                    LocaleController.getString(R.string.Reactions);
            case LawxConfig.DOUBLE_TAP_ACTION_TRANSLATE ->
                    LocaleController.getString(R.string.TranslateMessage);
            case LawxConfig.DOUBLE_TAP_ACTION_REPLY -> LocaleController.getString(R.string.Reply);
            case LawxConfig.DOUBLE_TAP_ACTION_SAVE ->
                    LocaleController.getString(R.string.AddToSavedMessages);
            case LawxConfig.DOUBLE_TAP_ACTION_REPEAT -> LocaleController.getString(R.string.Repeat);
            case LawxConfig.DOUBLE_TAP_ACTION_EDIT -> LocaleController.getString(R.string.Edit);
            default -> LocaleController.getString(R.string.Disable);
        };
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(StickerSizeCellFactory.of(stickerSizeRow, LocaleController.getString(R.string.StickerSize), LawxConfig.stickerSize, (progress, stop) -> {
            LawxConfig.setStickerSizeInMemory(progress);
            var item = listView.findItemByItemId(stickerSizeRow);
            if (item != null) {
                item.floatValue = progress;
            }
            if (stop) {
                LawxConfig.setStickerSize(progress);
            }
            if (progress != 14.0f && resetItem.getVisibility() != View.VISIBLE) {
                AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
            }
        }).slug("stickerSize"));
        items.add(UItem.asCheck(hideTimeOnStickerRow, LocaleController.getString(R.string.HideTimeOnSticker)).slug("hideTimeOnSticker").setChecked(LawxConfig.hideTimeOnSticker));
        items.add(UItem.asCheck(showTimeHintRow, LocaleController.getString(R.string.ShowTimeHint), LocaleController.getString(R.string.ShowTimeHintDesc)).slug("showTimeHint").setChecked(LawxConfig.showTimeHint));
        items.add(UItem.asCheck(reducedColorsRow, LocaleController.getString(R.string.ReducedColors)).slug("reducedColors").setChecked(LawxConfig.reducedColors));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Chat)));
        items.add(UItem.asCheck(ignoreBlockedRow, LocaleController.getString(R.string.IgnoreBlocked), LocaleController.getString(R.string.IgnoreBlockedAbout)).slug("ignoreBlocked").setChecked(LawxConfig.ignoreBlocked));
        items.add(UItem.asCheck(quickForwardRow, LocaleController.getString(R.string.QuickForward)).slug("quickForward").setChecked(LawxConfig.quickForward));
        items.add(UItem.asCheck(hideKeyboardOnChatScrollRow, LocaleController.getString(R.string.HideKeyboardOnChatScroll)).slug("hideKeyboardOnChatScroll").setChecked(LawxConfig.hideKeyboardOnChatScroll));
        items.add(TextSettingsCellFactory.of(inlineBotsRow, LocaleController.getString(R.string.InlineBotsManage)).slug("inlineBotsManage"));
        items.add(UItem.asCheck(showInlineBotManageButtonRow, LocaleController.getString(R.string.ShowInlineBotManageButton), LocaleController.getString(R.string.ShowInlineBotManageButtonDesc)).slug("showInlineBotManageButton").setChecked(LawxConfig.showInlineBotManageButton));
        items.add(UItem.asCheck(tryToOpenAllLinksInIVRow, LocaleController.getString(R.string.OpenAllLinksInInstantView)).slug("tryToOpenAllLinksInIV").setChecked(LawxConfig.tryToOpenAllLinksInIV));
        items.add(UItem.asCheck(disableJumpToNextRow, LocaleController.getString(R.string.DisableJumpToNextChannel)).slug("disableJumpToNext").setChecked(LawxConfig.disableJumpToNextChannel));
        items.add(UItem.asCheck(disableGreetingStickerRow, LocaleController.getString(R.string.DisableGreetingSticker)).slug("disableGreetingSticker").setChecked(LawxConfig.disableGreetingSticker));
        items.add(UItem.asCheck(hideChannelBottomButtonsRow, LocaleController.getString(R.string.HideChannelBottomButtons)).slug("hideChannelBottomButtons").setChecked(LawxConfig.hideChannelBottomButtons));
        items.add(TextSettingsCellFactory.of(doubleTapActionRow, LocaleController.getString(R.string.DoubleTapAction), LawxConfig.doubleTapInAction == LawxConfig.doubleTapOutAction ?
                getDoubleTapActionText(LawxConfig.doubleTapInAction) :
                getDoubleTapActionText(LawxConfig.doubleTapInAction) + ", " + getDoubleTapActionText(LawxConfig.doubleTapOutAction)).slug("doubleTapAction"));
        items.add(TextSettingsCellFactory.of(maxRecentStickersRow, LocaleController.getString(R.string.MaxRecentStickers), String.valueOf(LawxConfig.maxRecentStickers)).slug("maxRecentStickers"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.PremiumPreviewVoiceToText)));
        items.add(TextSettingsCellFactory.of(transcribeProviderRow, LocaleController.getString(R.string.TranscribeProviderShort), switch (LawxConfig.transcribeProvider) {
            case LawxConfig.TRANSCRIBE_AUTO ->
                    LocaleController.getString(R.string.TranscribeProviderAuto);
            case LawxConfig.TRANSCRIBE_WORKERSAI ->
                    LocaleController.getString(R.string.TranscribeProviderWorkersAI);
            default -> LocaleController.getString(R.string.TelegramPremium);
        }).slug("transcribeProvider"));
        items.add(TextSettingsCellFactory.of(cfCredentialsRow, LocaleController.getString(R.string.CloudflareCredentials), "").slug("cfCredentials"));
        items.add(UItem.asShadow(LocaleController.formatString(R.string.TranscribeProviderDesc, LocaleController.getString(R.string.TranscribeProviderWorkersAI))));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Markdown)));
        items.add(UItem.asCheck(markdownEnableRow, LocaleController.getString(R.string.MarkdownEnableByDefault)).slug("markdownEnable").setChecked(!LawxConfig.disableMarkdownByDefault));
        items.add(TextSettingsCellFactory.of(markdownParserRow, LocaleController.getString(R.string.MarkdownParser), LawxConfig.newMarkdownParser ? "lawXgram" : "Telegram").slug("markdownParser"));
        if (LawxConfig.newMarkdownParser) {
            items.add(UItem.asCheck(markdownParseLinksRow, LocaleController.getString(R.string.MarkdownParseLinks)).slug("markdownParseLinks").setChecked(LawxConfig.markdownParseLinks));
        }
        items.add(UItem.asShadow(markdown2Row, TextUtils.expandTemplate(EntitiesHelper.parseMarkdown(LawxConfig.newMarkdownParser && LawxConfig.markdownParseLinks ? LocaleController.getString(R.string.MarkdownAbout) : LocaleController.getString(R.string.MarkdownAbout2)), "**", "__", "~~", "`", "||", "[", "](", ")")));

        items.add(UItem.asHeader(LocaleController.getString(R.string.SharedMediaTab2)));
        if (VoiceEnhancementsHelper.isAvailable()) {
            items.add(UItem.asCheck(voiceEnhancementsRow, LocaleController.getString(R.string.VoiceEnhancements), LocaleController.getString(R.string.VoiceEnhancementsAbout)).slug("voiceEnhancements").setChecked(LawxConfig.voiceEnhancements));
        }
        items.add(UItem.asCheck(rearVideoMessagesRow, LocaleController.getString(R.string.RearVideoMessages)).slug("rearVideoMessages").setChecked(LawxConfig.rearVideoMessages));
        items.add(UItem.asCheck(confirmAVRow, LocaleController.getString(R.string.ConfirmAVMessage)).slug("confirmAV").setChecked(LawxConfig.confirmAVMessage));
        items.add(UItem.asCheck(disableProximityEventsRow, LocaleController.getString(R.string.DisableProximityEvents)).slug("disableProximityEvents").setChecked(LawxConfig.disableProximityEvents));
        items.add(UItem.asCheck(disableVoiceMessageAutoPlayRow, LocaleController.getString(R.string.DisableVoiceMessagesAutoPlay)).slug("disableVoiceMessageAutoPlay").setChecked(LawxConfig.disableVoiceMessageAutoPlay));
        items.add(UItem.asCheck(unmuteVideosWithVolumeButtonsRow, LocaleController.getString(R.string.UnmuteVideosWithVolumeButtons)).slug("unmuteVideosWithVolumeButtons").setChecked(LawxConfig.unmuteVideosWithVolumeButtons));
        items.add(UItem.asCheck(autoPauseVideoRow, LocaleController.getString(R.string.AutoPauseVideo), LocaleController.getString(R.string.AutoPauseVideoAbout)).slug("autoPauseVideo").setChecked(LawxConfig.autoPauseVideo));
        items.add(UItem.asCheck(preferOriginalQualityRow, LocaleController.getString(R.string.PreferOriginalQuality), LocaleController.getString(R.string.PreferOriginalQualityDesc)).slug("preferOriginalQuality").setChecked(LawxConfig.preferOriginalQuality));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MessageMenu)));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 1, LocaleController.getString(R.string.DeleteDownloadedFile)).slug("showDeleteDownloadedFile").setChecked(LawxConfig.showDeleteDownloadedFile));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 2, LocaleController.getString(R.string.NoQuoteForward)).slug("showNoQuoteForward").setChecked(LawxConfig.showNoQuoteForward));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 3, LocaleController.getString(R.string.AddToSavedMessages)).slug("showAddToSavedMessages").setChecked(LawxConfig.showAddToSavedMessages));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 4, LocaleController.getString(R.string.Repeat)).slug("showRepeat").setChecked(LawxConfig.showRepeat));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 5, LocaleController.getString(R.string.Prpr)).slug("showPrPr").setChecked(LawxConfig.showPrPr));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 6, LocaleController.getString(R.string.TranslateMessage)).slug("showTranslate").setChecked(LawxConfig.showTranslate));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 7, LocaleController.getString(R.string.ReportChat)).slug("showReport").setChecked(LawxConfig.showReport));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 8, LocaleController.getString(R.string.MessageDetails)).slug("showMessageDetails").setChecked(LawxConfig.showMessageDetails));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 9, LocaleController.getString(R.string.CopyPhoto)).slug("showCopyPhoto").setChecked(LawxConfig.showCopyPhoto));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 10, LocaleController.getString(R.string.SetReminder)).slug("showSetReminder").setChecked(LawxConfig.showSetReminder));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 11, LocaleController.getString(R.string.QrCode)).slug("showQrCode").setChecked(LawxConfig.showQrCode));
        items.add(TextCheckbox2CellFactory.of(messageMenuRow + 12, LocaleController.getString(R.string.OpenInExternalApp)).slug("showOpenIn").setChecked(LawxConfig.showOpenIn));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id == ignoreBlockedRow) {
            LawxConfig.toggleIgnoreBlocked();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.ignoreBlocked);
            }
        } else if (id == hideKeyboardOnChatScrollRow) {
            LawxConfig.toggleHideKeyboardOnChatScroll();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.hideKeyboardOnChatScroll);
            }
        } else if (id == inlineBotsRow) {
            presentFragment(new LawxInlineBotsActivity());
        } else if (id == showInlineBotManageButtonRow) {
            LawxConfig.toggleShowInlineBotManageButton();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.showInlineBotManageButton);
            }
        } else if (id == rearVideoMessagesRow) {
            LawxConfig.toggleRearVideoMessages();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.rearVideoMessages);
            }
        } else if (id == confirmAVRow) {
            LawxConfig.toggleConfirmAVMessage();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.confirmAVMessage);
            }
        } else if (id == disableProximityEventsRow) {
            LawxConfig.toggleDisableProximityEvents();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.disableProximityEvents);
            }
            MediaController mediaController = MediaController.getInstanceIfCreated();
            if (mediaController != null) {
                mediaController.syncDisableProximityEvents();
            }
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService != null) {
                voIPService.syncDisableProximityEvents();
            }
        } else if (id == tryToOpenAllLinksInIVRow) {
            LawxConfig.toggleTryToOpenAllLinksInIV();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.tryToOpenAllLinksInIV);
            }
        } else if (id == autoPauseVideoRow) {
            LawxConfig.toggleAutoPauseVideo();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.autoPauseVideo);
            }
        } else if (id == disableJumpToNextRow) {
            LawxConfig.toggleDisableJumpToNextChannel();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.disableJumpToNextChannel);
            }
        } else if (id == disableGreetingStickerRow) {
            LawxConfig.toggleDisableGreetingSticker();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.disableGreetingSticker);
            }
        } else if (id == disableVoiceMessageAutoPlayRow) {
            LawxConfig.toggleDisableVoiceMessageAutoPlay();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.disableVoiceMessageAutoPlay);
            }
        } else if (id == unmuteVideosWithVolumeButtonsRow) {
            LawxConfig.toggleUnmuteVideosWithVolumeButtons();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.unmuteVideosWithVolumeButtons);
            }
        } else if (id == doubleTapActionRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.Disable));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_NONE);
            arrayList.add(LocaleController.getString(R.string.Reactions));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_REACTION);
            arrayList.add(LocaleController.getString(R.string.TranslateMessage));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_TRANSLATE);
            arrayList.add(LocaleController.getString(R.string.Reply));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_REPLY);
            arrayList.add(LocaleController.getString(R.string.AddToSavedMessages));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_SAVE);
            arrayList.add(LocaleController.getString(R.string.Repeat));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_REPEAT);
            arrayList.add(LocaleController.getString(R.string.Edit));
            types.add(LawxConfig.DOUBLE_TAP_ACTION_EDIT);

            var context = getParentActivity();
            var builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(LocaleController.getString(R.string.DoubleTapAction));

            var linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);

            var messagesCell = new ThemePreviewMessagesCell(context, parentLayout, 0);
            messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            linearLayout.addView(messagesCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            var hLayout = new LinearLayout(context);
            hLayout.setOrientation(LinearLayout.HORIZONTAL);
            hLayout.setPadding(0, AndroidUtilities.dp(8), 0, 0);
            linearLayout.addView(hLayout);

            for (int i = 0; i < 2; i++) {
                var out = i == 1;
                var layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                hLayout.addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, .5f));

                for (int a = 0; a < arrayList.size(); a++) {

                    var cell = new RadioColorCell(context, resourcesProvider);
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    cell.setTextAndValue(arrayList.get(a), a == types.indexOf(out ? LawxConfig.doubleTapOutAction : LawxConfig.doubleTapInAction));
                    cell.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), out ? AndroidUtilities.dp(6) : 0, out ? 0 : AndroidUtilities.dp(6), out ? 0 : AndroidUtilities.dp(6), out ? AndroidUtilities.dp(6) : 0));
                    layout.addView(cell);
                    cell.setOnClickListener(v -> {
                        var which = (Integer) v.getTag();
                        var old = out ? LawxConfig.doubleTapOutAction : LawxConfig.doubleTapInAction;
                        if (types.get(which) == old) {
                            return;
                        }
                        if (out) {
                            LawxConfig.setDoubleTapOutAction(types.get(which));
                        } else {
                            LawxConfig.setDoubleTapInAction(types.get(which));
                        }
                        ((RadioColorCell) layout.getChildAt(types.indexOf(old))).setChecked(false, true);
                        cell.setChecked(true, true);
                        item.textValue = LawxConfig.doubleTapInAction == LawxConfig.doubleTapOutAction ?
                                getDoubleTapActionText(LawxConfig.doubleTapInAction) :
                                getDoubleTapActionText(LawxConfig.doubleTapInAction) + ", " + getDoubleTapActionText(LawxConfig.doubleTapOutAction);
                        listView.adapter.notifyItemChanged(position, PARTIAL);
                    });
                }
            }

            builder.setOnPreDismissListener(dialog -> listView.adapter.notifyItemChanged(position, PARTIAL));
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.show();
        } else if (id == markdownEnableRow) {
            LawxConfig.toggleDisableMarkdownByDefault();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!LawxConfig.disableMarkdownByDefault);
            }
        } else if (id > messageMenuRow) {
            TextCheckbox2Cell cell = ((TextCheckbox2Cell) view);
            int menuPosition = id - messageMenuRow - 1;
            if (menuPosition == 0) {
                LawxConfig.toggleShowDeleteDownloadedFile();
                cell.setChecked(LawxConfig.showDeleteDownloadedFile);
            } else if (menuPosition == 1) {
                LawxConfig.toggleShowNoQuoteForward();
                cell.setChecked(LawxConfig.showNoQuoteForward);
            } else if (menuPosition == 2) {
                LawxConfig.toggleShowAddToSavedMessages();
                cell.setChecked(LawxConfig.showAddToSavedMessages);
            } else if (menuPosition == 3) {
                LawxConfig.toggleShowRepeat();
                cell.setChecked(LawxConfig.showRepeat);
            } else if (menuPosition == 4) {
                LawxConfig.toggleShowPrPr();
                cell.setChecked(LawxConfig.showPrPr);
            } else if (menuPosition == 5) {
                LawxConfig.toggleShowTranslate();
                cell.setChecked(LawxConfig.showTranslate);
            } else if (menuPosition == 6) {
                LawxConfig.toggleShowReport();
                cell.setChecked(LawxConfig.showReport);
            } else if (menuPosition == 7) {
                LawxConfig.toggleShowMessageDetails();
                cell.setChecked(LawxConfig.showMessageDetails);
            } else if (menuPosition == 8) {
                LawxConfig.toggleShowCopyPhoto();
                cell.setChecked(LawxConfig.showCopyPhoto);
            } else if (menuPosition == 9) {
                LawxConfig.toggleShowSetReminder();
                cell.setChecked(LawxConfig.showSetReminder);
            } else if (menuPosition == 10) {
                LawxConfig.toggleShowQrCode();
                cell.setChecked(LawxConfig.showQrCode);
            } else if (menuPosition == 11) {
                LawxConfig.toggleShowOpenIn();
                cell.setChecked(LawxConfig.showOpenIn);
            }
        } else if (id == voiceEnhancementsRow) {
            LawxConfig.toggleVoiceEnhancements();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.voiceEnhancements);
            }
        } else if (id == maxRecentStickersRow) {
            int[] counts = {20, 30, 40, 50, 80, 100, 120, 150, 180, 200};
            ArrayList<String> types = new ArrayList<>();
            for (int count : counts) {
                if (count <= getMessagesController().maxRecentStickersCount) {
                    types.add(String.valueOf(count));
                }
            }
            PopupHelper.show(types, LocaleController.getString(R.string.MaxRecentStickers), types.indexOf(String.valueOf(LawxConfig.maxRecentStickers)), getParentActivity(), view, i -> {
                LawxConfig.setMaxRecentStickers(Integer.parseInt(types.get(i)));
                item.textValue = String.valueOf(LawxConfig.maxRecentStickers);
                listView.adapter.notifyItemChanged(position, PARTIAL);
            }, resourcesProvider);
        } else if (id == hideTimeOnStickerRow) {
            LawxConfig.toggleHideTimeOnSticker();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.hideTimeOnSticker);
            }
            var stickerCell = listView.findViewByItemId(stickerSizeRow);
            if (stickerCell != null) stickerCell.invalidate();
        } else if (id == markdownParserRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("lawXgram");
            arrayList.add("Telegram");
            boolean oldParser = LawxConfig.newMarkdownParser;
            PopupHelper.show(arrayList, LocaleController.getString(R.string.MarkdownParser), LawxConfig.newMarkdownParser ? 0 : 1, getParentActivity(), view, i -> {
                LawxConfig.setNewMarkdownParser(i == 0);
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                if (oldParser != LawxConfig.newMarkdownParser) {
                    if (oldParser) {
                        notifyItemRemoved(markdownParseLinksRow);
                        updateRows();
                    } else {
                        updateRows();
                        notifyItemInserted(markdownParseLinksRow);
                    }
                    notifyItemChanged(markdown2Row);
                }
            }, resourcesProvider);
        } else if (id == markdownParseLinksRow) {
            LawxConfig.toggleMarkdownParseLinks();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.markdownParseLinks);
            }
            notifyItemChanged(markdown2Row);
        } else if (id == quickForwardRow) {
            LawxConfig.toggleQuickForward();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.quickForward);
            }
        } else if (id == reducedColorsRow) {
            LawxConfig.toggleReducedColors();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.reducedColors);
            }
            var stickerCell = listView.findViewByItemId(stickerSizeRow);
            stickerCell.invalidate();
        } else if (id == showTimeHintRow) {
            LawxConfig.toggleShowTimeHint();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.showTimeHint);
            }
        } else if (id == transcribeProviderRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.TranscribeProviderAuto));
            types.add(LawxConfig.TRANSCRIBE_AUTO);
            arrayList.add(LocaleController.getString(R.string.TelegramPremium));
            types.add(LawxConfig.TRANSCRIBE_PREMIUM);
            arrayList.add(LocaleController.getString(R.string.TranscribeProviderWorkersAI));
            types.add(LawxConfig.TRANSCRIBE_WORKERSAI);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.TranscribeProviderShort), types.indexOf(LawxConfig.transcribeProvider), getParentActivity(), view, i -> {
                LawxConfig.setTranscribeProvider(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
            }, resourcesProvider);
        } else if (id == cfCredentialsRow) {
            WhisperHelper.showCfCredentialsDialog(this);
        } else if (id == preferOriginalQualityRow) {
            LawxConfig.togglePreferOriginalQuality();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.preferOriginalQuality);
            }
        } else if (id == hideChannelBottomButtonsRow) {
            LawxConfig.toggleHideChannelBottomButtons();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.hideChannelBottomButtons);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.Chat);
    }

    @Override
    protected String getKey() {
        return "c";
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    private static class StickerSizeCellFactory extends UItem.UItemFactory<StickerSizeCell> {
        static {
            setup(new StickerSizeCellFactory());
        }

        @Override
        public StickerSizeCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new StickerSizeCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            var cell = (StickerSizeCell) view;
            var frameLayout = (FrameLayout) listView.getParent();
            cell.setFragmentView(frameLayout);
            cell.setValue(item.floatValue);
            cell.setOnDragListener((AltSeekbar.OnDrag) item.object);
        }

        public static UItem of(int id, String title, float value, AltSeekbar.OnDrag onDrag) {
            var item = UItem.ofFactory(StickerSizeCellFactory.class);
            item.id = id;
            item.text = title;
            item.object = onDrag;
            item.floatValue = value;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }

    private static class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final AltSeekbar sizeBar;

        private AltSeekbar.OnDrag onDrag;

        public StickerSizeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            setWillNotDraw(false);

            sizeBar = new AltSeekbar(context, (progress, stop) -> {
                setValue(progress);
                if (onDrag != null) onDrag.run(progress, stop);
            }, 2, 20, LocaleController.getString(R.string.StickerSize), LocaleController.getString(R.string.StickerSizeLeft), LocaleController.getString(R.string.StickerSizeRight), resourcesProvider);
            sizeBar.setValue(LawxConfig.stickerSize);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            messagesCell = new StickerSizePreviewMessagesCell(context, resourcesProvider);
            messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 112, 0, 0));
        }

        public void setOnDragListener(AltSeekbar.OnDrag onDrag) {
            this.onDrag = onDrag;
        }

        public void setFragmentView(FrameLayout fragmentView) {
            messagesCell.setFragmentView(fragmentView);
        }

        public void setValue(float value) {
            sizeBar.setValue(value);
            messagesCell.invalidate();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
        }
    }

    @SuppressLint("ViewConstructor")
    private static class AltSeekbar extends FrameLayout {

        private final AnimatedTextView headerValue;
        private final TextView leftTextView;
        private final TextView rightTextView;
        private final SeekBarView seekBarView;
        private final Theme.ResourcesProvider resourcesProvider;

        private final int min, max;
        private float currentValue;
        private int roundedValue;
        private boolean dragging;
        private boolean dragCommitted;

        public interface OnDrag {
            void run(float progress, boolean stop);
        }

        public AltSeekbar(Context context, AltSeekbar.OnDrag onDrag, int min, int max, String title, String left, String right, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            this.max = max;
            this.min = min;

            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            TextView headerTextView = new TextView(context);
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            headerTextView.setText(title);
            headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            headerValue = new AnimatedTextView(context, false, true, true) {
                final Drawable backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), 0.15f));

                @Override
                protected void onDraw(Canvas canvas) {
                    backgroundDrawable.setBounds(0, 0, (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()), getMeasuredHeight());
                    backgroundDrawable.draw(canvas);

                    super.onDraw(canvas);
                }
            };
            headerValue.setAnimationProperties(.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
            headerValue.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            headerValue.setPadding(AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2), AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2));
            headerValue.setTextSize(AndroidUtilities.dp(12));
            headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            headerLayout.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 17, 21, 0));

            seekBarView = new SeekBarView(context, true, resourcesProvider);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    currentValue = min + (max - min) * progress;
                    if (stop) {
                        dragCommitted = true;
                    }
                    onDrag.run(currentValue, stop);
                    if (Math.round(currentValue) != roundedValue) {
                        roundedValue = Math.round(currentValue);
                        updateText();
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                    if (pressed) {
                        dragging = true;
                        dragCommitted = false;
                    } else if (dragging) {
                        dragging = false;
                        if (!dragCommitted) {
                            dragCommitted = true;
                            onDrag.run(currentValue, true);
                        }
                    }
                }
            });
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38 + 6, Gravity.TOP, 6, 68, 6, 0));

            FrameLayout valuesView = new FrameLayout(context);

            leftTextView = new TextView(context);
            leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            leftTextView.setGravity(Gravity.LEFT);
            leftTextView.setText(left);
            valuesView.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            rightTextView = new TextView(context);
            rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            rightTextView.setGravity(Gravity.RIGHT);
            rightTextView.setText(right);
            valuesView.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

            addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 52, 21, 0));
        }

        private void updateValues() {
            int middle = (max - min) / 2 + min;
            if (currentValue >= middle * 1.5f - min * 0.5f) {
                rightTextView.setTextColor(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                        (currentValue - (middle * 1.5f - min * 0.5f)) / (max - (middle * 1.5f - min * 0.5f))
                ));
                leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            } else if (currentValue <= (middle + min) * 0.5f) {
                leftTextView.setTextColor(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                        (currentValue - (middle + min) * 0.5f) / (min - (middle + min) * 0.5f)
                ));
                rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            } else {
                leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            }
        }

        public void setValue(float value) {
            currentValue = value;
            seekBarView.setProgress((value - min) / (float) (max - min));
            if (Math.round(currentValue) != roundedValue) {
                roundedValue = Math.round(currentValue);
                updateText();
            }
        }

        private void updateText() {
            headerValue.cancelAnimation();
            headerValue.setText(getTextForHeader(), true);
            updateValues();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(112), MeasureSpec.EXACTLY)
            );
        }

        public CharSequence getTextForHeader() {
            CharSequence text;
            if (roundedValue == min) {
                text = leftTextView.getText();
            } else if (roundedValue == max) {
                text = rightTextView.getText();
            } else {
                text = String.valueOf(roundedValue);
            }
            return text.toString().toUpperCase();
        }
    }
}
