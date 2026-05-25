package ru.llacker.lawxgram.settings;

import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Locale;
import java.lang.ref.WeakReference;

import ru.llacker.lawxgram.LawxEnvironment;
import ru.llacker.lawxgram.LawxConfig;
import ru.llacker.lawxgram.helpers.AnalyticsHelper;
import ru.llacker.lawxgram.helpers.PopupHelper;
import ru.llacker.lawxgram.helpers.SettingsHelper;
import ru.llacker.lawxgram.helpers.remote.UpdateHelper;

public class LawxExperimentalSettingsActivity extends BaseLawxSettingsActivity {

    private final int downloadSpeedBoostRow = rowId++;
    private final int keepFormattingRow = rowId++;
    private final int autoInlineBotRow = rowId++;
    private final int forceFontWeightFallbackRow = rowId++;
    private final int mapDriftingFixRow = rowId++;
    private final int contentRestrictionRow = rowId++;
    private final int showRPCErrorRow = rowId++;

    private final int checkUpdateRow = rowId++;

    private final int sendBugReportRow = rowId++;
    private final int deleteDataRow = rowId++;
    private final int copyReportIdRow = rowId++;

    private final int deleteAccountRow = rowId++;
    private DeleteAccountOperation deleteAccountOperation;
    private CountDownTimer deleteAccountTimer;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.Experiment)));
        if (!MessagesController.getInstance(currentAccount).getfileExperimentalParams) {
            items.add(TextSettingsCellFactory.of(downloadSpeedBoostRow, LocaleController.getString(R.string.DownloadSpeedBoost), switch (LawxConfig.downloadSpeedBoost) {
                case LawxConfig.BOOST_NONE ->
                        LocaleController.getString(R.string.DownloadSpeedBoostNone);
                case LawxConfig.BOOST_EXTREME ->
                        LocaleController.getString(R.string.DownloadSpeedBoostExtreme);
                default -> LocaleController.getString(R.string.DownloadSpeedBoostAverage);
            }).slug("downloadSpeedBoost"));
        }
        items.add(UItem.asCheck(keepFormattingRow, LocaleController.getString(R.string.TranslationKeepFormatting)).slug("keepFormatting").setChecked(LawxConfig.keepFormatting));
        items.add(UItem.asCheck(autoInlineBotRow, LocaleController.getString(R.string.AutoInlineBot), LocaleController.getString(R.string.AutoInlineBotDesc)).slug("autoInlineBot").setChecked(LawxConfig.autoInlineBot));
        items.add(UItem.asCheck(forceFontWeightFallbackRow, LocaleController.getString(R.string.ForceFontWeightFallback)).slug("forceFontWeightFallback").setChecked(LawxConfig.forceFontWeightFallback));
        items.add(UItem.asCheck(mapDriftingFixRow, LocaleController.getString(R.string.MapDriftingFix)).slug("mapDriftingFix").setChecked(LawxConfig.mapDriftingFix));
        if (LawxEnvironment.isDirectApp()) {
            items.add(UItem.asCheck(contentRestrictionRow, LocaleController.getString(R.string.IgnoreContentRestriction)).slug("contentRestriction").setChecked(LawxConfig.ignoreContentRestriction));
        }
        items.add(UItem.asCheck(showRPCErrorRow, LocaleController.getString(R.string.ShowRPCError), LocaleController.formatString(R.string.ShowRPCErrorException, "FILE_REFERENCE_EXPIRED")).slug("showRPCError").setChecked(LawxConfig.showRPCError));
        items.add(UItem.asShadow(null));

        if (getParentActivity() instanceof LaunchActivity) {
            items.add(TextDetailSettingsCellFactory.of(checkUpdateRow, LocaleController.getString(R.string.CheckUpdate), UpdateHelper.formatDateUpdate(SharedConfig.lastUpdateCheckTime)).slug("checkUpdate"));
            items.add(UItem.asShadow(null));
        }

        if (AnalyticsHelper.isSettingsAvailable()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.SendAnonymousData)));
            items.add(UItem.asCheck(sendBugReportRow, LocaleController.getString(R.string.SendBugReport), LocaleController.getString(R.string.SendBugReportDesc)).slug("sendBugReport").setChecked(!AnalyticsHelper.analyticsDisabled && AnalyticsHelper.sendBugReport).setEnabled(!AnalyticsHelper.analyticsDisabled));
            items.add(TextDetailSettingsCellFactory.of(deleteDataRow, LocaleController.getString(R.string.AnonymousDataDelete), LocaleController.getString(R.string.AnonymousDataDeleteDesc)).slug("deleteData"));
        }
        items.add(TextDetailSettingsCellFactory.of(copyReportIdRow, LocaleController.getString(R.string.CopyReportId), LocaleController.getString(R.string.CopyReportIdDescription)).slug("copyReportId"));
        items.add(UItem.asShadow(!AnalyticsHelper.isSettingsAvailable() ? null : LocaleController.formatString(R.string.SendAnonymousDataDesc, "Sentry", "Functional Software")));

        items.add(TextSettingsCellFactory.of(deleteAccountRow, LocaleController.getString(R.string.DeleteAccount), "").slug("deleteAccount").red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (false) {
            var builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            var message = new TextView(getParentActivity());
            message.setText(getSpannedString(R.string.SoonRemovedOption, "https://t.me/" + LocaleController.getString(R.string.OfficialChannelUsername)));
            message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            message.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
            message.setHighlightColor(getThemedColor(Theme.key_dialogLinkSelection));
            message.setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);
            message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
            message.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            builder.setView(message);
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            showDialog(builder.create());
        }
        if (id == deleteAccountRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            builder.setMessage(LocaleController.getString(R.string.TosDeclineDeleteAccount));
            builder.setTitle(LocaleController.getString(R.string.DeleteAccount));
            builder.setPositiveButton(LocaleController.getString(R.string.Deactivate), (dialog, which) -> {
                if (BuildConfig.DEBUG) return;
                final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);

                ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(getMessagesController().getAllDialogs());
                for (TLRPC.Dialog TLdialog : dialogs) {
                    if (TLdialog instanceof TLRPC.TL_dialogFolder) {
                        continue;
                    }
                    TLRPC.Peer peer = getMessagesController().getPeer((int) TLdialog.id);
                    if (peer.channel_id != 0) {
                        TLRPC.Chat chat = getMessagesController().getChat(peer.channel_id);
                        if (!chat.broadcast) {
                            getMessageHelper().deleteUserHistoryWithSearch(LawxExperimentalSettingsActivity.this, TLdialog.id);
                        }
                    }
                    if (peer.user_id != 0) {
                        getMessagesController().deleteDialog(TLdialog.id, 0, true);
                    }
                }

                DeleteAccountOperation operation = new DeleteAccountOperation(this, progressDialog, currentAccount);
                deleteAccountOperation = operation;
                Utilities.globalQueue.postRunnable(operation, 20000);
                progressDialog.show();
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialog1 -> {
                var button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setTextColor(getThemedColor(Theme.key_text_RedBold));
                button.setEnabled(false);
                var buttonText = button.getText();
                button.setText(String.format(Locale.getDefault(), "%s (%d)", buttonText, 60));
                cancelDeleteAccountTimer();
                deleteAccountTimer = new CountDownTimer(60000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        button.setText(String.format(Locale.getDefault(), "%s (%d)", buttonText, millisUntilFinished / 1000 + 1));
                    }

                    @Override
                    public void onFinish() {
                        button.setText(buttonText);
                        button.setEnabled(true);
                    }
                }.start();
            });
            showDialog(dialog, dialog1 -> cancelDeleteAccountTimer());
        } else if (id == mapDriftingFixRow) {
            LawxConfig.toggleMapDriftingFix();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.mapDriftingFix);
            }
        } else if (id == showRPCErrorRow) {
            LawxConfig.toggleShowRPCError();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.showRPCError);
            }
        } else if (id == downloadSpeedBoostRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostNone));
            types.add(LawxConfig.BOOST_NONE);
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostAverage));
            types.add(LawxConfig.BOOST_AVERAGE);
            arrayList.add(LocaleController.getString(R.string.DownloadSpeedBoostExtreme));
            types.add(LawxConfig.BOOST_EXTREME);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.DownloadSpeedBoost), types.indexOf(LawxConfig.downloadSpeedBoost), getParentActivity(), view, i -> {
                LawxConfig.setDownloadSpeedBoost(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
            }, resourcesProvider);
        } else if (id == sendBugReportRow) {
            if (AnalyticsHelper.analyticsDisabled) {
                return;
            }
            AnalyticsHelper.toggleSendBugReport();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AnalyticsHelper.sendBugReport);
            }
            var copyItem = listView.findItemByItemId(copyReportIdRow);
            copyItem.setEnabled(AnalyticsHelper.sendBugReport);
            notifyItemChanged(copyReportIdRow);
        } else if (id == deleteDataRow) {
            if (AnalyticsHelper.analyticsDisabled) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
            builder.setTitle(LocaleController.getString(R.string.AnonymousDataDelete));
            builder.setMessage(LocaleController.getString(R.string.AnonymousDataDeleteDesc));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                AnalyticsHelper.setAnalyticsDisabled();
                listView.adapter.update(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            dialog.redPositive();
        } else if (id == contentRestrictionRow) {
            LawxConfig.toggleIgnoreContentRestriction();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.ignoreContentRestriction);
            }
        } else if (id == copyReportIdRow) {
            if (AnalyticsHelper.analyticsDisabled || !AnalyticsHelper.sendBugReport) {
                return;
            }
            SettingsHelper.copyReportId();
        } else if (id == autoInlineBotRow) {
            LawxConfig.toggleAutoInlineBot();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.autoInlineBot);
            }
        } else if (id == forceFontWeightFallbackRow) {
            LawxConfig.toggleForceFontWeightFallback();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.forceFontWeightFallback);
            }
            showRestartBulletin();
        } else if (id == keepFormattingRow) {
            LawxConfig.toggleKeepFormatting();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.keepFormatting);
            }
        } else if (id == checkUpdateRow) {
            if (getParentActivity() instanceof LaunchActivity launchActivity) {
                launchActivity.checkAppUpdate(true, new Browser.Progress() {
                    @Override
                    public void end() {
                        item.subtext = UpdateHelper.formatDateUpdate(SharedConfig.lastUpdateCheckTime);
                        listView.adapter.notifyItemChanged(position);
                    }
                });
                item.subtext = LocaleController.getString(R.string.CheckingUpdate);
                listView.adapter.notifyItemChanged(position);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        cancelDeleteAccountTimer();
        DeleteAccountOperation operation = deleteAccountOperation;
        if (operation != null) {
            operation.detachUi();
            deleteAccountOperation = null;
        }
        super.onFragmentDestroy();
    }

    private void cancelDeleteAccountTimer() {
        CountDownTimer currentTimer = deleteAccountTimer;
        deleteAccountTimer = null;
        if (currentTimer != null) {
            currentTimer.cancel();
        }
    }

    private void onDeleteAccountOperationFinished(DeleteAccountOperation operation) {
        if (deleteAccountOperation == operation) {
            deleteAccountOperation = null;
        }
    }

    private static class DeleteAccountOperation implements Runnable {
        private final WeakReference<LawxExperimentalSettingsActivity> fragmentRef;
        private final int account;
        private AlertDialog progressDialog;

        private DeleteAccountOperation(LawxExperimentalSettingsActivity fragment, AlertDialog progressDialog, int account) {
            fragmentRef = new WeakReference<>(fragment);
            this.progressDialog = progressDialog;
            this.account = account;
        }

        @Override
        public void run() {
            TL_account.deleteAccount req = new TL_account.deleteAccount();
            req.reason = "Meow";
            AccountInstance.getInstance(account).getConnectionsManager().sendRequest(req, createCallback());
        }

        private RequestDelegate createCallback() {
            return (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                dismissProgressDialog();
                try {
                    AccountInstance accountInstance = AccountInstance.getInstance(account);
                    if (response instanceof TLRPC.TL_boolTrue) {
                        accountInstance.getMessagesController().performLogout(0);
                    } else if (error == null || error.code != -1000) {
                        LawxExperimentalSettingsActivity fragment = fragmentRef.get();
                        if (fragment == null || fragment.isFinished || fragment.getParentActivity() == null) {
                            return;
                        }
                        String errorText = LocaleController.getString(R.string.ErrorOccurred);
                        if (error != null) {
                            errorText += "\n" + error.text;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.resourcesProvider);
                        builder.setTitle(LocaleController.getString(R.string.AppName));
                        builder.setMessage(errorText);
                        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                        builder.show();
                    }
                } finally {
                    LawxExperimentalSettingsActivity fragment = fragmentRef.get();
                    if (fragment != null) {
                        fragment.onDeleteAccountOperationFinished(this);
                    }
                }
            });
        }

        private void detachUi() {
            dismissProgressDialog();
        }

        private void dismissProgressDialog() {
            AlertDialog currentProgressDialog = progressDialog;
            progressDialog = null;
            if (currentProgressDialog == null) {
                return;
            }
            try {
                currentProgressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.NotificationsOther);
    }

    @Override
    protected String getKey() {
        return "e";
    }

    @Override
    public Integer getSelectorColor(int position) {
        var item = listView.adapter.getItem(position);
        if (item.id == deleteAccountRow) {
            return Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .1f);
        }
        return super.getSelectorColor(position);
    }
}
