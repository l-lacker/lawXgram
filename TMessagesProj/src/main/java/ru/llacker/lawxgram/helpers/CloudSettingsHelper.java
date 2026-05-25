package ru.llacker.lawxgram.helpers;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ru.llacker.lawxgram.LawxConfig;

public class CloudSettingsHelper {
    private static final int CONFIG_VERSION = 0;
    private static final String PREFS_NAME = "lawxcloud";
    private static final String LEGACY_PREFS_NAME = "nekocloud";
    private static final String CLOUD_SETTINGS_KEY = "lawx_settings";
    private static final String LEGACY_CLOUD_SETTINGS_KEY = "neko_settings";
    private static final String CLOUD_SETTINGS_UPDATED_AT_KEY = "lawx_settings_updated_at";
    private static final String LEGACY_CLOUD_SETTINGS_UPDATED_AT_KEY = "neko_settings_updated_at";

    private final SharedPreferences preferences = PreferencesMigrationHelper.getSharedPreferences(ApplicationLoader.applicationContext, PREFS_NAME, LEGACY_PREFS_NAME);
    private final long[] cloudSyncedDate = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private long localSyncedDate = preferences.getLong("updated_at", -1);
    private boolean autoSync = preferences.getBoolean("auto_sync", false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cloudSyncRunnable = () -> {
        if (!autoSync) {
            return;
        }
        CloudSettingsHelper.getInstance().syncToCloud((success, error) -> {
            if (!success) {
                var global = BulletinFactory.global();
                if (error == null) {
                    global.createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.CloudConfigSyncFailed)).show();
                } else {
                    global.createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.CloudConfigSyncFailed), error).show();
                }
            }
        });
    };

    private static final class InstanceHolder {
        private static final CloudSettingsHelper instance = new CloudSettingsHelper();
    }

    public static CloudSettingsHelper getInstance() {
        return InstanceHolder.instance;
    }

    public void showDialog(BaseFragment parentFragment) {
        if (parentFragment == null) {
            return;
        }

        Context context = parentFragment.getParentActivity();
        if (context == null || parentFragment.isFinished) {
            return;
        }
        Theme.ResourcesProvider resourcesProvider = parentFragment.getResourceProvider();
        int selectedAccount = UserConfig.selectedAccount;
        CloudDialogState dialogState = new CloudDialogState(parentFragment, selectedAccount);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.CloudConfig));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.CloudConfigDesc)));
        builder.setTopImage(R.drawable.cloud, Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider));

        TextViewSwitcher syncedDate = new TextViewSwitcher(context);
        syncedDate.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setGravity(Gravity.START);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            return tv;
        });
        syncedDate.setInAnimation(context, R.anim.alpha_in);
        syncedDate.setOutAnimation(context, R.anim.alpha_out);
        dialogState.setSyncedDateView(syncedDate);
        syncedDate.setText(formatSyncedDate(selectedAccount), false);

        getCloudItem(selectedAccount, CLOUD_SETTINGS_UPDATED_AT_KEY, LEGACY_CLOUD_SETTINGS_UPDATED_AT_KEY, (res, error) -> {
            if (error == null && AndroidUtilities.isNumeric(res)) {
                cloudSyncedDate[selectedAccount] = Long.parseLong(res);
            } else {
                cloudSyncedDate[selectedAccount] = -1;
            }
            dialogState.updateSyncedDate();
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        ButtonWithCounterView buttonTextView = new ButtonWithCounterView(context, true, resourcesProvider).setRound();
        buttonTextView.setText(LocaleController.getString(R.string.CloudConfigSync), false);
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 0, 16, 0));
        buttonTextView.setOnClickListener(view -> {
            dialogState.setSyncedDateText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CloudConfigSyncing)));
            syncToCloud(selectedAccount, (success, error) -> {
                dialogState.updateSyncedDate();
                if (!success) {
                    dialogState.showBulletin(R.string.CloudConfigSyncFailed, error);
                }
            });
        });

        ButtonWithCounterView textView = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        textView.setText(LocaleController.getString(R.string.CloudConfigRestore), false);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));
        textView.setOnClickListener(view -> {
            dialogState.setSyncedDateText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CloudConfigSyncing)));
            restoreFromCloud(selectedAccount, (success, error) -> {
                dialogState.updateSyncedDate();
                if (!success) {
                    dialogState.showBulletin(R.string.CloudConfigRestoreFailed, error);
                }
            });
        });

        MiniCheckBoxCell autoSyncCheck = new MiniCheckBoxCell(context, 8, resourcesProvider);
        autoSyncCheck.setTextAndValueAndCheck(LocaleController.getString(R.string.CloudConfigAutoSync), LocaleController.getString(R.string.CloudConfigAutoSyncDesc), autoSync);
        autoSyncCheck.setOnClickListener(view13 -> {
            autoSync = !autoSync;
            preferences.edit().putBoolean("auto_sync", autoSync).apply();
            autoSyncCheck.setChecked(autoSync);
        });
        linearLayout.addView(autoSyncCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

        linearLayout.addView(syncedDate, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 0));

        builder.setView(linearLayout);
        Dialog dialog = parentFragment.showDialog(builder.create(), dialogInterface -> dialogState.dismiss());
        if (dialog == null) {
            dialogState.dismiss();
        }
    }

    public void doAutoSync() {
        handler.removeCallbacks(cloudSyncRunnable);
        if (!autoSync) {
            return;
        }
        handler.postDelayed(cloudSyncRunnable, 1200);
    }

    private void syncToCloud(Utilities.Callback2<Boolean, String> callback) {
        syncToCloud(UserConfig.selectedAccount, callback);
    }

    private void syncToCloud(int account, Utilities.Callback2<Boolean, String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            String rawConfig = LawxConfig.exportConfigs();
            String compressed = encodeConfig(rawConfig);
            String config = rawConfig.length() >= compressed.length() ? compressed : rawConfig;
            getCloudStorageHelper(account).setItemAsync(CLOUD_SETTINGS_KEY, config, (res, error) -> {
                if (error == null) {
                    localSyncedDate = cloudSyncedDate[account] = System.currentTimeMillis();
                    getCloudStorageHelper(account).setItem(CLOUD_SETTINGS_UPDATED_AT_KEY, String.valueOf(localSyncedDate), null);
                    preferences.edit().putLong("updated_at", localSyncedDate).apply();
                    callback.run(true, null);
                } else {
                    callback.run(false, error);
                }
            });
        });
    }

    private void restoreFromCloud(Utilities.Callback2<Boolean, String> callback) {
        restoreFromCloud(UserConfig.selectedAccount, callback);
    }

    private void restoreFromCloud(int account, Utilities.Callback2<Boolean, String> callback) {
        getCloudItem(account, CLOUD_SETTINGS_KEY, LEGACY_CLOUD_SETTINGS_KEY, (res, error) -> {
            if (error == null) {
                if (TextUtils.isEmpty(res)) {
                    callback.run(false, "EMPTY_CONFIG");
                } else {
                    Utilities.globalQueue.postRunnable(() -> {
                        String config = decodeConfig(res);
                        if (config == null) {
                            AndroidUtilities.runOnUIThread(() -> callback.run(false, "DECODE_FAILED"));
                            return;
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                LawxConfig.importConfigs(config);
                                localSyncedDate = System.currentTimeMillis();
                                preferences.edit().putLong("updated_at", localSyncedDate).apply();
                                callback.run(true, null);
                            } catch (Exception e) {
                                FileLog.e(e);
                                callback.run(false, e.getLocalizedMessage());
                            }
                        });
                    });
                }
            } else {
                callback.run(false, error);
            }
        });
    }

    private CloudStorageHelper getCloudStorageHelper(int account) {
        return CloudStorageHelper.getInstance(account);
    }

    private void getCloudItem(int account, String key, String legacyKey, Utilities.Callback2<String, String> callback) {
        getCloudStorageHelper(account).getItem(key, (res, error) -> {
            if (error == null && !TextUtils.isEmpty(res)) {
                callback.run(res, null);
                return;
            }
            getCloudStorageHelper(account).getItem(legacyKey, callback);
        });
    }

    private String formatSyncedDate(int account) {
        return LocaleController.formatString(
                R.string.CloudConfigSyncDate,
                localSyncedDate > 0 ? formatDateUntil(localSyncedDate) : LocaleController.getString(R.string.CloudConfigSyncDateNever),
                cloudSyncedDate[account] > 0 ? formatDateUntil(cloudSyncedDate[account]) : LocaleController.getString(R.string.CloudConfigSyncDateNever));
    }

    public static String encodeConfig(String string) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(string.length());
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(string.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            byte[] compressed = bos.toByteArray();
            return CONFIG_VERSION + Base64.encodeToString(compressed, Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return string;
        }
    }

    private static String decodeConfig(String string) {
        if (string.startsWith("{")) {
            return string;
        } else if (string.startsWith(String.valueOf(CONFIG_VERSION))) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decode(string.substring(1), Base64.DEFAULT));
                 GZIPInputStream gis = new GZIPInputStream(bis);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = gis.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                return bos.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private static String formatDateUntil(long date) {
        try {
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (year == dateYear) {
                return LocaleController.getInstance().getFormatterBannedUntilThisYear().format(new Date(date));
            } else {
                return LocaleController.getInstance().getFormatterBannedUntil().format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    @SuppressLint("ViewConstructor")
    private static class MiniCheckBoxCell extends FrameLayout {

        private final TextView textView;
        private final TextView valueTextView;
        private final CheckBoxSquare checkBox;

        public MiniCheckBoxCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            ScaleStateListAnimator.apply(this, .02f, 1.2f);

            setForeground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 22, 22));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_dialogIcon, resourcesProvider));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 22 + padding : padding, 4, LocaleController.isRTL ? padding : 22 + padding, 4));

            checkBox = new CheckBoxSquare(context, true, resourcesProvider);
            addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? padding : 4, 0, LocaleController.isRTL ? 4 : padding, 0));
        }

        public void setTextAndValueAndCheck(String text, String value, boolean checked) {
            textView.setText(text);
            valueTextView.setText(value);
            checkBox.setChecked(checked, false);
        }

        public void setChecked(boolean checked) {
            checkBox.setChecked(checked, true);
        }

        public boolean isChecked() {
            return checkBox.isChecked();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked(checkBox.isChecked());
            StringBuilder sb = new StringBuilder();
            sb.append(textView.getText());
            if (!TextUtils.isEmpty(valueTextView.getText())) {
                sb.append('\n');
                sb.append(valueTextView.getText());
            }
            info.setContentDescription(sb);
        }
    }

    private class CloudDialogState {
        private final WeakReference<BaseFragment> fragmentRef;
        private final WeakReference<Theme.ResourcesProvider> resourcesProviderRef;
        private final int account;
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private WeakReference<TextViewSwitcher> syncedDateRef;

        private CloudDialogState(BaseFragment fragment, int account) {
            fragmentRef = new WeakReference<>(fragment);
            resourcesProviderRef = new WeakReference<>(fragment.getResourceProvider());
            this.account = account;
        }

        private void setSyncedDateView(TextViewSwitcher syncedDate) {
            syncedDateRef = new WeakReference<>(syncedDate);
        }

        private boolean isAlive() {
            BaseFragment fragment = fragmentRef.get();
            return alive.get() && fragment != null && !fragment.isFinished && AndroidUtilities.isActivityRunning(fragment.getParentActivity());
        }

        private void setSyncedDateText(CharSequence text) {
            if (!isAlive() || syncedDateRef == null) {
                return;
            }
            TextViewSwitcher syncedDate = syncedDateRef.get();
            if (syncedDate != null) {
                syncedDate.setText(text);
            }
        }

        private void updateSyncedDate() {
            setSyncedDateText(formatSyncedDate(account));
        }

        private void showBulletin(int stringResId, String error) {
            if (!isAlive()) {
                return;
            }
            BaseFragment fragment = fragmentRef.get();
            Context context = fragment != null ? fragment.getParentActivity() : null;
            if (context == null) {
                return;
            }
            Theme.ResourcesProvider resourcesProvider = resourcesProviderRef.get();
            if (error == null) {
                BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(stringResId)).show();
            } else {
                BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(stringResId), error).show();
            }
        }

        private void dismiss() {
            alive.set(false);
        }
    }
}
