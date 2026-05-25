package ru.llacker.lawxgram;

import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.ArrayList;
import java.util.HashMap;

import ru.llacker.lawxgram.helpers.UserHelper;
import ru.llacker.lawxgram.settings.BaseLawxSettingsActivity;

public class DatacenterPopupWrapper {

    private static final long DATACENTER_CHECK_CACHE_TIME = 2 * 60 * 1000L;
    private static final long DATACENTER_CHECK_TIMEOUT = 60_000L;
    private static final ArrayList<DatacenterInfo> datacenterInfos = new ArrayList<>(5) {{
        for (int a = 1; a <= 5; a++) {
            add(new DatacenterInfo(a));
        }
    }};

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
    private final Theme.ResourcesProvider resourcesProvider;
    private final HashMap<DatacenterInfo, ActionBarMenuSubItem> datacenterItems = new HashMap<>(5);
    private final HashMap<DatacenterInfo, Integer> activeCheckGenerations = new HashMap<>(5);
    private boolean disposed;

    public DatacenterPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, swipeBackLayout != null ? 0 : R.drawable.popup_fixed_alert4, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);
        windowLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                disposed = false;
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                disposed = true;
                cancelPendingDatacenterChecks();
            }
        });

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());

            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        for (var datacenterInfo : datacenterInfos) {
            var item = ActionBarMenuItem.addItem(windowLayout, 0, UserHelper.formatDCString(datacenterInfo.id), false, resourcesProvider);
            item.setTag(datacenterInfo);
            datacenterItems.put(datacenterInfo, item);
            item.setOnClickListener(view -> {
                if (datacenterInfo.checking) {
                    return;
                }
                checkDatacenter(item, true);
            });
            updateStatus(item, resourcesProvider, false);
            checkDatacenter(item, false);
        }

        ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);

        var textView = new LinkSpanDrawable.LinksTextView(context);
        textView.setTag(R.id.fit_width_tag, 1);
        textView.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        textView.setText(BaseLawxSettingsActivity.getSpannedString(R.string.DatacenterStatusAbout, "https://core.telegram.org/api/datacenter"));
        windowLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 0));
    }

    private void checkDatacenter(ActionBarMenuSubItem item, boolean force) {
        var datacenterInfo = (DatacenterInfo) item.getTag();
        if (datacenterInfo.checking) {
            return;
        }
        if (!force && SystemClock.elapsedRealtime() - datacenterInfo.availableCheckTime < DATACENTER_CHECK_CACHE_TIME) {
            return;
        }
        datacenterInfo.checking = true;
        int generation = ++datacenterInfo.checkGeneration;
        activeCheckGenerations.put(datacenterInfo, generation);
        updateStatus(item, resourcesProvider, true);
        Runnable timeoutRunnable = () -> finishDatacenterCheck(datacenterInfo, generation, null);
        datacenterInfo.timeoutRunnable = timeoutRunnable;
        AndroidUtilities.runOnUIThread(timeoutRunnable, DATACENTER_CHECK_TIMEOUT);
        datacenterInfo.pingId = ConnectionsManager.getInstance(UserConfig.selectedAccount).checkProxy("ping.neko", datacenterInfo.id, null, null, null, time -> AndroidUtilities.runOnUIThread(() -> {
            finishDatacenterCheck(datacenterInfo, generation, time);
        }));
    }

    private void finishDatacenterCheck(DatacenterInfo datacenterInfo, int generation, Long time) {
        if (datacenterInfo.checkGeneration != generation) {
            return;
        }
        cancelDatacenterTimeout(datacenterInfo);
        activeCheckGenerations.remove(datacenterInfo);
        boolean wasChecking = datacenterInfo.checking;
        datacenterInfo.checking = false;
        if (time == null) {
            updateDatacenterStatus(datacenterInfo);
            return;
        }
        if (time != -1 || wasChecking) {
            datacenterInfo.availableCheckTime = SystemClock.elapsedRealtime();
            if (time == -1) {
                datacenterInfo.available = false;
                datacenterInfo.ping = 0;
            } else {
                datacenterInfo.ping = time;
                datacenterInfo.available = true;
            }
        }
        updateDatacenterStatus(datacenterInfo);
    }

    private void updateDatacenterStatus(DatacenterInfo datacenterInfo) {
        if (disposed) {
            return;
        }
        ActionBarMenuSubItem item = datacenterItems.get(datacenterInfo);
        if (item == null) {
            return;
        }
        updateStatus(item, resourcesProvider, true);
    }

    private void cancelPendingDatacenterChecks() {
        if (activeCheckGenerations.isEmpty()) {
            return;
        }
        for (DatacenterInfo datacenterInfo : new ArrayList<>(activeCheckGenerations.keySet())) {
            Integer generation = activeCheckGenerations.get(datacenterInfo);
            if (generation != null) {
                finishDatacenterCheck(datacenterInfo, generation, null);
            }
        }
    }

    private void cancelDatacenterTimeout(DatacenterInfo datacenterInfo) {
        Runnable timeoutRunnable = datacenterInfo.timeoutRunnable;
        if (timeoutRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            datacenterInfo.timeoutRunnable = null;
        }
    }

    public void updateStatus(ActionBarMenuSubItem item, Theme.ResourcesProvider resourcesProvider, boolean animated) {
        var datacenterInfo = (DatacenterInfo) item.getTag();
        int colorKey;
        if (datacenterInfo.checking) {
            item.setSubtext(LocaleController.getString(R.string.Checking), animated);
            colorKey = Theme.key_windowBackgroundWhiteGrayText2;
        } else if (datacenterInfo.available) {
            if (datacenterInfo.ping >= 1000) {
                item.setSubtext(LocaleController.formatString(R.string.Ping, datacenterInfo.ping), animated);
                colorKey = Theme.key_text_RedRegular;
            } else if (datacenterInfo.ping != 0) {
                item.setSubtext(LocaleController.formatString(R.string.Ping, datacenterInfo.ping), animated);
                colorKey = Theme.key_windowBackgroundWhiteGreenText;
            } else {
                item.setSubtext(LocaleController.getString(R.string.Available), animated);
                colorKey = Theme.key_windowBackgroundWhiteGreenText;
            }
        } else {
            item.setSubtext(LocaleController.getString(R.string.Unavailable), animated);
            colorKey = Theme.key_text_RedRegular;
        }
        item.setSubtextColor(Theme.getColor(colorKey, resourcesProvider));
    }

    private static class DatacenterInfo {

        public int id;

        public long pingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;
        public int checkGeneration;
        public Runnable timeoutRunnable;

        public DatacenterInfo(int i) {
            id = i;
        }
    }
}
