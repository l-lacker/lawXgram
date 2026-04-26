package ru.llacker.lawxgram.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;

import java.util.ArrayList;

import ru.llacker.lawxgram.LawxConfig;

public class LawxBottomTabsSettingsActivity extends BaseLawxSettingsActivity {

    private static final int TAB_ROW_ID = 1000;

    private final int previewRow = rowId++;
    private BottomTabsPreview previewView;

    @Override
    public View createView(Context context) {
        previewView = new BottomTabsPreview(context);
        View view = super.createView(context);
        listView.listenReorder((id, items) -> {
            int[] order = new int[items.size()];
            for (int i = 0; i < items.size(); i++) {
                order[i] = items.get(i).id - TAB_ROW_ID;
            }
            LawxConfig.setMainTabsOrder(order);
            previewView.update();
        });
        listView.allowReorder(true);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (previewView == null && getContext() != null) {
            previewView = new BottomTabsPreview(getContext());
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.BottomNavigationButtons)));
        UItem previewItem = UItem.asCustom(previewView, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48);
        previewItem.id = previewRow;
        items.add(previewItem);
        items.add(UItem.asShadow(LocaleController.getString(R.string.BottomNavigationButtonsAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BottomNavigationButtonsShown)));
        if (adapter != null) {
            adapter.reorderSectionStart();
        }
        int[] order = LawxConfig.getMainTabsOrder();
        for (int tab : order) {
            items.add(UItem.asCheck(TAB_ROW_ID + tab, getTabTitle(tab))
                    .setChecked(LawxConfig.isMainTabVisible(tab))
                    .slug(getTabSlug(tab)));
        }
        if (adapter != null) {
            adapter.reorderSectionEnd();
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.BottomNavigationButtonsHint)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        int tab = item.id - TAB_ROW_ID;
        if (tab < 0 || tab >= LawxConfig.MAIN_TABS_COUNT) {
            return;
        }
        LawxConfig.setMainTabVisible(tab, !LawxConfig.isMainTabVisible(tab));
        item.checked = LawxConfig.isMainTabVisible(tab);
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(item.checked);
        }
        if (previewView != null) {
            previewView.update();
        }
        listView.adapter.update(true);
    }

    private CharSequence getTabTitle(int tab) {
        return switch (tab) {
            case LawxConfig.MAIN_TAB_CHATS -> LocaleController.getString(R.string.MainTabsChats);
            case LawxConfig.MAIN_TAB_CONTACTS -> LocaleController.getString(R.string.MainTabsContacts);
            case LawxConfig.MAIN_TAB_SETTINGS -> LocaleController.getString(UserConfig.getInstance(currentAccount).showCallsTab ? R.string.MainTabsCalls : R.string.Settings);
            case LawxConfig.MAIN_TAB_PROFILE -> LocaleController.getString(R.string.MainTabsProfile);
            default -> "";
        };
    }

    private String getTabSlug(int tab) {
        return switch (tab) {
            case LawxConfig.MAIN_TAB_CHATS -> "chats";
            case LawxConfig.MAIN_TAB_CONTACTS -> "contacts";
            case LawxConfig.MAIN_TAB_SETTINGS -> "settings";
            case LawxConfig.MAIN_TAB_PROFILE -> "profile";
            default -> "";
        };
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.BottomNavigationButtons);
    }

    private class BottomTabsPreview extends FrameLayout {
        private final MainTabsLayout tabsLayout;
        private final GlassTabView[] tabs = new GlassTabView[LawxConfig.MAIN_TABS_COUNT];
        private int touchTab = -1;
        private int dragTab = -1;
        private float touchX;
        private float touchY;
        private float dragX;
        private boolean orderChanged;
        private Runnable longPressRunnable;

        public BottomTabsPreview(Context context) {
            super(context);
            setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));

            FrameLayout box = new FrameLayout(context);
            box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(28), getThemedColor(Theme.key_windowBackgroundWhite)));
            addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 20, Gravity.CENTER, 16, 0, 16, 0));

            tabsLayout = new MainTabsLayout(context);
            tabsLayout.setPadding(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN));
            tabsLayout.setBackground(createTabsBackground());
            box.addView(tabsLayout, LayoutHelper.createFrame(328 + DialogsActivity.MAIN_TABS_MARGIN * 2, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.CENTER));

            tabs[LawxConfig.MAIN_TAB_CHATS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats);
            tabs[LawxConfig.MAIN_TAB_CONTACTS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts);
            tabs[LawxConfig.MAIN_TAB_SETTINGS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings);
            tabs[LawxConfig.MAIN_TAB_PROFILE] = GlassTabView.createAvatar(context, resourceProvider, currentAccount, R.string.MainTabsProfile);

            for (int i = 0; i < tabs.length; i++) {
                final int tabId = i;
                tabs[i].setOnTouchListener((v, event) -> onPreviewTouch(tabId, event));
                tabsLayout.addView(tabs[i]);
                tabsLayout.setViewVisible(tabs[i], false, false);
            }
            update();
        }

        private GradientDrawable createTabsBackground() {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Theme.getColor(Theme.key_glass_targetMainTabs, resourceProvider));
            drawable.setCornerRadius(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS / 2f));
            drawable.setStroke(AndroidUtilities.dp(1), Theme.multAlpha(Theme.isCurrentThemeDark() ? 0xFFFFFFFF : 0xFF000000, 0.10f));
            return drawable;
        }

        private boolean onPreviewTouch(int tab, MotionEvent event) {
            if (!LawxConfig.isMainTabVisible(tab)) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchTab = tab;
                dragTab = -1;
                touchX = dragX = event.getRawX();
                touchY = event.getRawY();
                orderChanged = false;
                cancelLongPress();
                if (getVisibleTabsCount() > 1) {
                    longPressRunnable = () -> {
                        if (touchTab == tab && getVisibleTabsCount() > 1) {
                            dragTab = tab;
                            tabs[tab].performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
                    };
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && touchTab == tab) {
                if (dragTab != tab) {
                    if (Math.abs(event.getRawX() - touchX) > AndroidUtilities.touchSlop || Math.abs(event.getRawY() - touchY) > AndroidUtilities.touchSlop) {
                        cancelPreviewLongPress();
                    }
                    return true;
                }
                float dx = event.getRawX() - dragX;
                if (Math.abs(dx) < AndroidUtilities.dp(28)) {
                    return true;
                }
                if (movePreviewTab(tab, dx > 0)) {
                    dragX = event.getRawX();
                    orderChanged = true;
                }
                return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                cancelPreviewLongPress();
                if (event.getActionMasked() == MotionEvent.ACTION_UP && dragTab != tab && !orderChanged && getVisibleTabsCount() > 1 && Math.abs(event.getRawX() - touchX) <= AndroidUtilities.touchSlop && Math.abs(event.getRawY() - touchY) <= AndroidUtilities.touchSlop) {
                    LawxConfig.setMainTabVisible(tab, !LawxConfig.isMainTabVisible(tab));
                    tabs[tab].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    update();
                    listView.adapter.update(true);
                } else if (orderChanged) {
                    listView.adapter.update(true);
                }
                touchTab = -1;
                dragTab = -1;
                return true;
            }
            return false;
        }

        private void cancelPreviewLongPress() {
            if (longPressRunnable != null) {
                removeCallbacks(longPressRunnable);
                longPressRunnable = null;
            }
        }

        private int getVisibleTabsCount() {
            int count = 0;
            for (int tab = 0; tab < LawxConfig.MAIN_TABS_COUNT; tab++) {
                if (LawxConfig.isMainTabVisible(tab)) {
                    count++;
                }
            }
            return count;
        }

        private boolean movePreviewTab(int tab, boolean forward) {
            int[] order = LawxConfig.getMainTabsOrder();
            int from = -1;
            for (int i = 0; i < order.length; i++) {
                if (order[i] == tab) {
                    from = i;
                    break;
                }
            }
            if (from < 0) {
                return false;
            }
            int to = from + (forward ? 1 : -1);
            while (to >= 0 && to < order.length && !LawxConfig.isMainTabVisible(order[to])) {
                to += forward ? 1 : -1;
            }
            if (to < 0 || to >= order.length) {
                return false;
            }
            int moved = order[from];
            if (from < to) {
                System.arraycopy(order, from + 1, order, from, to - from);
            } else {
                System.arraycopy(order, to, order, to + 1, from - to);
            }
            order[to] = moved;
            LawxConfig.setMainTabsOrder(order);
            update();
            return true;
        }

        public void update() {
            int[] order = LawxConfig.getMainTabsOrder();
            boolean callsTab = UserConfig.getInstance(currentAccount).showCallsTab;
            int selectedTab = -1;
            tabs[LawxConfig.MAIN_TAB_SETTINGS].setText(LocaleController.getString(callsTab ? R.string.MainTabsCalls : R.string.Settings));
            tabs[LawxConfig.MAIN_TAB_SETTINGS].setTabAnimation(callsTab ? GlassTabView.TabAnimation.CALLS : GlassTabView.TabAnimation.SETTINGS);
            for (int i = 0; i < order.length; i++) {
                int tab = order[i];
                if (selectedTab < 0 && LawxConfig.isMainTabVisible(tab)) {
                    selectedTab = tab;
                }
                tabsLayout.setPriority(tabs[tab], i);
                tabsLayout.setViewVisible(tabs[tab], LawxConfig.isMainTabVisible(tab), true);
            }
            for (int tab : order) {
                tabs[tab].setSelected(tab == selectedTab, false);
            }
            tabsLayout.requestLayout();
        }
    }
}
