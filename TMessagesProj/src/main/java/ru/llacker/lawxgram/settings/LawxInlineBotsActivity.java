package ru.llacker.lawxgram.settings;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class LawxInlineBotsActivity extends BaseLawxSettingsActivity {

    private static final int DONE_BUTTON = 1;
    private static final int SEARCH_ID = 2;

    private final ArrayList<Long> selectedOrder = new ArrayList<>();
    private final ArrayList<TLRPC.User> searchResults = new ArrayList<>();
    private SearchAdapterHelper searchAdapterHelper;
    private String searchQuery;

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) {
            return false;
        }
        searchAdapterHelper = new SearchAdapterHelper(true) {
            @Override
            protected boolean filter(TLObject obj) {
                return obj instanceof TLRPC.User && isAvailableInlineBot((TLRPC.User) obj);
            }
        };
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int searchId) {
                if (searchId == SEARCH_ID) {
                    updateSearchResults();
                }
            }

            @Override
            public boolean canApplySearchResults(int searchId) {
                return !isFinished && searchId == SEARCH_ID;
            }
        });
        loadSelectedBots();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        if (searchAdapterHelper != null) {
            searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, 0, false, 0, SEARCH_ID);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.listenReorder((id, items) -> {
            selectedOrder.clear();
            for (int i = 0; i < items.size(); i++) {
                selectedOrder.add(items.get(i).longValue);
            }
        });
        listView.allowReorder(true);
        return view;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        ActionBarMenu menu = actionBar.createMenu();
        createSearchItem(menu, new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                searchQuery = null;
                searchResults.clear();
                searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, 0, false, 0, SEARCH_ID);
                listView.allowReorder(true);
                listView.adapter.update(true);
            }

            @Override
            public void onSearchExpand() {
                listView.allowReorder(false);
                listView.adapter.update(true);
            }

            @Override
            public void onTextChanged(EditText editText) {
                search(editText.getText().toString());
            }
        });
        menu.addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == DONE_BUTTON) {
                    getMediaDataController().setManualInlineBots(new ArrayList<>(selectedOrder));
                    finishFragment();
                }
            }
        });
        return actionBar;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (isSearchFieldVisible() && !TextUtils.isEmpty(searchQuery)) {
            fillSearchItems(items);
            return;
        }

        ArrayList<TLRPC.User> selectedBots = getSelectedBots();
        ArrayList<TLRPC.User> availableBots = getAvailableBots();

        if (!selectedBots.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.SelectedBots)));
            if (adapter != null) {
                adapter.reorderSectionStart();
            }
            for (int i = 0; i < selectedBots.size(); i++) {
                items.add(botItem(selectedBots.get(i), true));
            }
            if (adapter != null) {
                adapter.reorderSectionEnd();
            }
        }

        if (!availableBots.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.ChannelBots)));
            for (int i = 0; i < availableBots.size(); i++) {
                items.add(botItem(availableBots.get(i), false));
            }
        } else if (selectedBots.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.NoResult)));
        }
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        long id = item.longValue;
        if (id <= 0) {
            return;
        }
        if (selectedOrder.contains(id)) {
            selectedOrder.remove(id);
        } else {
            selectedOrder.add(id);
        }
        listView.hideSelector(false);
        listView.adapter.update(true);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.InlineBotsManage);
    }

    private void search(String query) {
        searchQuery = query == null ? null : query.trim();
        if (searchQuery != null && searchQuery.startsWith("@")) {
            searchQuery = searchQuery.substring(1).trim();
        }
        searchResults.clear();
        if (TextUtils.isEmpty(searchQuery)) {
            searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, 0, false, 0, SEARCH_ID);
            listView.adapter.update(true);
            return;
        }
        addLocalSearchResults(searchQuery);
        searchAdapterHelper.queryServerSearch(searchQuery, true, false, true, false, false, 0, false, 0, SEARCH_ID);
        listView.adapter.update(true);
    }

    private void updateSearchResults() {
        if (TextUtils.isEmpty(searchQuery)) {
            return;
        }
        searchResults.clear();
        addLocalSearchResults(searchQuery);
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
        for (int i = 0; i < globalSearch.size(); i++) {
            TLObject object = globalSearch.get(i);
            if (object instanceof TLRPC.User user) {
                addSearchResult(user);
            }
        }
        listView.adapter.update(true);
    }

    private void addLocalSearchResults(String query) {
        String lowerQuery = query.toLowerCase();
        ArrayList<TLRPC.User> bots = getSelectedBots();
        bots.addAll(getAvailableBots());
        for (int i = 0; i < bots.size(); i++) {
            TLRPC.User user = bots.get(i);
            String username = UserObject.getPublicUsername(user);
            String name = UserObject.getUserName(user);
            if ((!TextUtils.isEmpty(username) && username.toLowerCase().contains(lowerQuery)) || (!TextUtils.isEmpty(name) && name.toLowerCase().contains(lowerQuery))) {
                addSearchResult(user);
            }
        }
    }

    private void addSearchResult(TLRPC.User user) {
        if (!isAvailableInlineBot(user)) {
            return;
        }
        for (int i = 0; i < searchResults.size(); i++) {
            if (searchResults.get(i).id == user.id) {
                return;
            }
        }
        searchResults.add(user);
    }

    private void fillSearchItems(ArrayList<UItem> items) {
        if (!searchResults.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.Search)));
            for (int i = 0; i < searchResults.size(); i++) {
                TLRPC.User user = searchResults.get(i);
                items.add(botItem(user, selectedOrder.contains(user.id)));
            }
        } else {
            items.add(UItem.asShadow(LocaleController.getString(R.string.NoResult)));
        }
    }

    private UItem botItem(TLRPC.User user, boolean checked) {
        UItem item = UItem.asUserCheckbox((int) (user.id ^ (user.id >>> 32)), user);
        item.longValue = user.id;
        item.checked = checked;
        return item;
    }

    private void loadSelectedBots() {
        selectedOrder.clear();
        ArrayList<Long> ids = getMediaDataController().hasManualInlineBots() ? getMediaDataController().getManualInlineBotIds() : new ArrayList<>();
        if (ids.isEmpty() && !getMediaDataController().hasManualInlineBots()) {
            for (int i = 0; i < getMediaDataController().inlineBots.size(); i++) {
                ids.add(getMediaDataController().inlineBots.get(i).peer.user_id);
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            TLRPC.User user = getMessagesController().getUser(id);
            if (isAvailableInlineBot(user) && !selectedOrder.contains(id)) {
                selectedOrder.add(id);
            }
        }
    }

    private ArrayList<TLRPC.User> getSelectedBots() {
        ArrayList<TLRPC.User> bots = new ArrayList<>();
        for (int i = 0; i < selectedOrder.size(); i++) {
            TLRPC.User user = getMessagesController().getUser(selectedOrder.get(i));
            if (isAvailableInlineBot(user)) {
                bots.add(user);
            }
        }
        return bots;
    }

    private ArrayList<TLRPC.User> getAvailableBots() {
        ArrayList<TLRPC.User> bots = new ArrayList<>();
        for (int i = 0; i < getMessagesController().dialogsUsersOnly.size(); i++) {
            long id = getMessagesController().dialogsUsersOnly.get(i).id;
            TLRPC.User user = getMessagesController().getUser(id);
            if (isAvailableInlineBot(user) && !selectedOrder.contains(id)) {
                bots.add(user);
            }
        }
        return bots;
    }

    private boolean isAvailableInlineBot(TLRPC.User user) {
        return user != null && user.bot && !UserObject.isDeleted(user) && UserObject.getPublicUsername(user) != null && user.bot_inline_placeholder != null;
    }

}
