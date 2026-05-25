package ru.llacker.lawxgram.settings;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import ru.llacker.lawxgram.LawxConfig;
import ru.llacker.lawxgram.helpers.EmojiHelper;

public class LawxEmojiSettingsActivity extends BaseLawxSettingsActivity implements ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate {

    private static final int menu_delete = 0;
    private static final int menu_share = 1;

    private final ArrayList<EmojiHelper.EmojiPack> emojiPacks = new ArrayList<>();
    private final SparseBooleanArray selectedItems = new SparseBooleanArray();

    private final int useSystemEmojiRow = rowId++;

    private final int appleRow = rowId++;
    private final int emojiAddRow = rowId++;

    private final int emojiStartRow = 100;

    private volatile ChatAttachAlert chatAttachAlert;
    private NumberTextView selectedCountTextView;
    private volatile AlertDialog progressDialog;
    private volatile boolean destroyed;
    private volatile int fileOperationId;

    @Override
    public View createView(Context context) {
        destroyed = false;
        View view = super.createView(context);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        ActionBarMenu actionMode = actionBar.createActionMode();

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                } else if (id == menu_delete || id == menu_share) {
                    processSelectionMenu(id);
                }
            }
        });
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        selectedCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        actionMode.addItemWithWidth(menu_share, R.drawable.msg_share, AndroidUtilities.dp(54));
        actionMode.addItemWithWidth(menu_delete, R.drawable.msg_delete, AndroidUtilities.dp(54));
        return view;
    }

    public void updatePacks() {
        emojiPacks.clear();
        emojiPacks.addAll(EmojiHelper.getInstance().getEmojiPacksInfo());
    }

    @Override
    @SuppressLint("UseCompatLoadingForDrawables")
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        updatePacks();

        items.add(UItem.asHeader(LocaleController.getString(R.string.General)));
        items.add(UItem.asCheck(useSystemEmojiRow, LocaleController.getString(R.string.EmojiUseDefault)).setChecked(LawxConfig.useSystemEmoji).slug("useSystemEmoji"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.EmojiSets)));
        var selectedPackId = EmojiHelper.getInstance().getSelectedEmojiPackId();
        items.add(EmojiSetCellFactory.of(appleRow, EmojiHelper.DEFAULT_PACK, EmojiHelper.DEFAULT_PACK.getPackId().equals(selectedPackId) && !LawxConfig.useSystemEmoji, false));
        for (int i = 0, size = emojiPacks.size(); i < size; i++) {
            EmojiHelper.EmojiPack pack = emojiPacks.get(i);
            items.add(EmojiSetCellFactory.of(emojiStartRow + i, pack, pack.getPackId().equals(selectedPackId) && !LawxConfig.useSystemEmoji, false));
        }
        var drawable1 = getParentActivity().getDrawable(R.drawable.poll_add_circle);
        var drawable2 = getParentActivity().getDrawable(R.drawable.poll_add_plus);
        drawable1.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
        drawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
        items.add(TextCreationCellFactory.of(emojiAddRow, LocaleController.getString(R.string.AddEmojiSet), combinedDrawable).slug("emojiAdd"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.EmojiSetHint)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id == useSystemEmojiRow) {
            LawxConfig.toggleUseSystemEmoji();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.useSystemEmoji);
            }
            EmojiHelper.reloadEmoji();
            updateEmojiSets();
        } else if (id == emojiAddRow) {
            chatAttachAlert = new ChatAttachAlert(getParentActivity(), this, false, false);
            chatAttachAlert.setEmojiPicker();
            chatAttachAlert.init();
            chatAttachAlert.show();
        } else if (id == appleRow || id >= emojiStartRow) {
            EmojiSetCell cell = (EmojiSetCell) view;
            if (id != appleRow && hasSelected()) {
                toggleSelected(id);
            } else {
                if (cell.isChecked()) return;
                cell.setChecked(true, true);
                EmojiHelper.getInstance().setEmojiPack(cell.getPack().getPackId());
                updateEmojiSets();
                EmojiHelper.reloadEmoji();
                notifyItemChanged(useSystemEmojiRow, PARTIAL);
            }
        }
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id >= emojiStartRow) {
            toggleSelected(id);
            return true;
        }
        return super.onItemLongClick(item, view, position, x, y);
    }

    private void updateEmojiSets() {
        var selectedPackId = EmojiHelper.getInstance().getSelectedEmojiPackId();
        var appleItem = listView.findItemByItemId(appleRow);
        appleItem.checked = !hasSelected() && EmojiHelper.DEFAULT_PACK.getPackId().equals(selectedPackId) && !LawxConfig.useSystemEmoji;
        for (int i = 0, size = emojiPacks.size(); i < size; i++) {
            EmojiHelper.EmojiPack pack = emojiPacks.get(i);
            var item = listView.findItemByItemId(emojiStartRow + i);
            item.checked = !hasSelected() && pack.getPackId().equals(selectedPackId) && !LawxConfig.useSystemEmoji;
            item.object2 = selectedItems.get(item.id, false);
        }
        listView.adapter.notifyItemRangeChanged(listView.findPositionByItemId(appleRow), emojiPacks.size() + 1, PARTIAL);
    }

    public void toggleSelected(int id) {
        selectedItems.put(id, !selectedItems.get(id, false));
        updateEmojiSets();
        checkActionMode();
    }

    public boolean hasSelected() {
        return selectedItems.indexOfValue(true) != -1;
    }

    public void clearSelected() {
        selectedItems.clear();
        updateEmojiSets();
        checkActionMode();
    }

    public int getSelectedCount() {
        int count = 0;
        for (int i = 0, size = selectedItems.size(); i < size; i++) {
            if (selectedItems.valueAt(i)) {
                count++;
            }
        }
        return count;
    }

    private void checkActionMode() {
        int selectedCount = getSelectedCount();
        boolean actionModeShowed = actionBar.isActionModeShowed();
        if (selectedCount > 0) {
            selectedCountTextView.setNumber(selectedCount, actionModeShowed);
            if (!actionModeShowed) {
                actionBar.showActionMode();
            }
        } else if (actionModeShowed) {
            actionBar.hideActionMode();
        }
    }

    private void processSelectionMenu(int which) {
        ArrayList<EmojiHelper.EmojiPack> stickerSetList = new ArrayList<>(selectedItems.size());
        for (int i = 0, size = emojiPacks.size(); i < size; i++) {
            EmojiHelper.EmojiPack pack = emojiPacks.get(i);
            if (selectedItems.get(emojiStartRow + i, false)) {
                stickerSetList.add(pack);
            }
        }
        int count = stickerSetList.size();
        if (count > 1) {
            if (which == menu_share) {
                var intent = new Intent();
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("font/ttf");
                ArrayList<Uri> uriList = new ArrayList<>();
                for (EmojiHelper.EmojiPack packTmp : stickerSetList) {
                    uriList.add(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(packTmp.getFileLocation())));
                }
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                getParentActivity().startActivity(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)));
            } else {
                var builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
                builder.setTitle(LocaleController.formatString(R.string.DeleteStickerSetsAlertTitle, LocaleController.formatString(R.string.DeleteEmojiSets, count)));
                builder.setMessage(LocaleController.getString(R.string.DeleteEmojiSetsMessage));
                builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which1) -> {
                    if (destroyed || getParentActivity() == null) {
                        return;
                    }
                    progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.setCanCancel(false);
                    progressDialog.showDelayed(300);
                    AlertDialog currentProgressDialog = progressDialog;
                    Utilities.globalQueue.postRunnable(() -> {
                        for (int i = 0, size = stickerSetList.size(); i < size; i++) {
                            EmojiHelper.getInstance().deleteEmojiPack(stickerSetList.get(i));
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (currentProgressDialog == progressDialog) {
                                currentProgressDialog.dismiss();
                                progressDialog = null;
                            }
                            EmojiHelper.reloadEmoji();
                            if (destroyed || getParentActivity() == null || listView == null || listView.adapter == null) {
                                return;
                            }
                            clearSelected();
                            listView.adapter.update(true);
                        });
                    });
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                dialog.redPositive();
            }
        } else {
            var pack = stickerSetList.get(0);
            if (which == menu_share) {
                var intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("font/ttf");
                var uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(pack.getFileLocation()));
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                getParentActivity().startActivity(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)));
                clearSelected();
            } else {
                EmojiHelper.getInstance().cancelableDelete(this, pack, new DeleteEmojiPackBulletinAction(this, pack));
            }
        }
    }

    @Override
    protected String getKey() {
        return "emoji";
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.EmojiSets);
    }

    @Override
    public Integer getSelectorColor(int position) {
        var item = listView.adapter.getItem(position);
        if (item.id == emojiAddRow) {
            return Theme.multAlpha(getThemedColor(Theme.key_switchTrackChecked), .1f);
        }
        return super.getSelectorColor(position);
    }

    @Override
    public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<TLRPC.MessageEntity> captionEntities, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, long payStars) {
        ArrayList<File> filesToUpload = new ArrayList<>();
        for (String file : files) {
            File f = new File(file);
            if (f.exists()) {
                filesToUpload.add(f);
            }
        }
        processFiles(filesToUpload);
    }


    @Override
    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            photoPickerIntent.setType("font/*");
            startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static File copyFileToCache(Uri uri) {
        if (uri == null) {
            return null;
        }
        try (InputStream is = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return null;
            }
            String fileName = MediaController.getFileName(uri);
            File sharingDirectory = AndroidUtilities.getSharingDirectory();
            if (!sharingDirectory.exists() && !sharingDirectory.mkdirs()) {
                return null;
            }
            File dest = new File(sharingDirectory, fileName == null ? "Emoji.ttf" : fileName);
            try (FileOutputStream os = new FileOutputStream(dest)) {
                AndroidUtilities.copyFile(is, os);
            }
            return dest;
        } catch (IOException e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21) {
            if (data == null || destroyed || getParentActivity() == null) {
                return;
            }

            ChatAttachAlert currentAttachAlert = chatAttachAlert;
            if (currentAttachAlert != null) {
                if (currentAttachAlert.getDocumentLayout() == null) {
                    return;
                }
                int operationId = startFileOperation();
                if (operationId == 0) {
                    return;
                }
                WeakReference<LawxEmojiSettingsActivity> activityRef = new WeakReference<>(this);
                WeakReference<ChatAttachAlert> attachAlertRef = new WeakReference<>(currentAttachAlert);
                Uri dataUri = data.getData();
                ClipData clipData = data.getClipData();

                Utilities.globalQueue.postRunnable(() -> {
                    ArrayList<File> files = new ArrayList<>();
                    boolean hasInvalidFile = false;
                    LawxEmojiSettingsActivity activity = activityRef.get();
                    if (activity == null || !activity.isFileOperationActive(operationId)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            LawxEmojiSettingsActivity currentActivity = activityRef.get();
                            if (currentActivity != null) {
                                currentActivity.finishFileOperation(operationId);
                            }
                        });
                        return;
                    }
                    if (dataUri != null) {
                        File file = copyFileToCache(dataUri);
                        if (file != null && EmojiHelper.isValidEmojiPack(file)) {
                            files.add(file);
                        } else if (file != null) {
                            hasInvalidFile = true;
                        }
                    } else if (clipData != null) {
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            activity = activityRef.get();
                            if (activity == null || !activity.isFileOperationActive(operationId)) {
                                files.clear();
                                break;
                            }
                            File file = copyFileToCache(clipData.getItemAt(i).getUri());
                            if (file != null && EmojiHelper.isValidEmojiPack(file)) {
                                files.add(file);
                            } else {
                                hasInvalidFile = file != null;
                                files.clear();
                                break;
                            }
                        }
                    }
                    boolean finalHasInvalidFile = hasInvalidFile;
                    AndroidUtilities.runOnUIThread(() -> {
                        LawxEmojiSettingsActivity currentActivity = activityRef.get();
                        if (currentActivity == null) {
                            return;
                        }
                        ChatAttachAlert attachAlert = attachAlertRef.get();
                        if (!currentActivity.isFileOperationActive(operationId) || currentActivity.getParentActivity() == null || attachAlert == null || attachAlert != currentActivity.chatAttachAlert) {
                            currentActivity.finishFileOperation(operationId);
                            return;
                        }
                        if (!files.isEmpty()) {
                            attachAlert.dismiss();
                            currentActivity.chatAttachAlert = null;
                            currentActivity.processFiles(files, operationId);
                        } else {
                            currentActivity.finishFileOperation(operationId);
                            if (finalHasInvalidFile) {
                                currentActivity.showInvalidEmojiFontBulletin(attachAlert);
                            }
                        }
                    });
                });
            }
        }
    }

    public void processFiles(ArrayList<File> files) {
        if (files == null || files.isEmpty() || destroyed || getParentActivity() == null) {
            return;
        }
        int operationId = startFileOperation();
        if (operationId == 0) {
            return;
        }
        processFiles(files, operationId);
    }

    private void processFiles(ArrayList<File> files, int operationId) {
        ArrayList<File> filesToInstall = new ArrayList<>(files);
        WeakReference<LawxEmojiSettingsActivity> activityRef = new WeakReference<>(this);
        Utilities.globalQueue.postRunnable(() -> {
            int count = 0;
            for (var file : filesToInstall) {
                LawxEmojiSettingsActivity activity = activityRef.get();
                if (activity == null || !activity.isFileOperationActive(operationId)) {
                    break;
                }
                try {
                    if (EmojiHelper.getInstance().installEmoji(file) != null) {
                        count++;
                    }
                } catch (Exception e) {
                    FileLog.e("Emoji Font install failed", e);
                }
            }
            int finalCount = count;
            AndroidUtilities.runOnUIThread(() -> {
                LawxEmojiSettingsActivity activity = activityRef.get();
                if (activity == null) {
                    return;
                }
                boolean canUpdate = activity.isFileOperationActive(operationId) && !activity.destroyed && activity.getParentActivity() != null;
                activity.finishFileOperation(operationId);
                if (!canUpdate) {
                    return;
                }
                if (activity.listView != null && activity.listView.adapter != null) {
                    activity.listView.adapter.update(true);
                }
            });
        });
    }

    private int startFileOperation() {
        if (destroyed || getParentActivity() == null) {
            return 0;
        }
        int operationId = ++fileOperationId;
        if (progressDialog == null) {
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(300);
        }
        return operationId;
    }

    private boolean isFileOperationActive(int operationId) {
        return operationId != 0 && !destroyed && fileOperationId == operationId;
    }

    private void finishFileOperation(int operationId) {
        if (operationId != fileOperationId) {
            return;
        }
        fileOperationId++;
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void showInvalidEmojiFontBulletin(ChatAttachAlert attachAlert) {
        BulletinFactory.of(attachAlert.getContainer(), null).createErrorBulletinSubtitle(
                LocaleController.formatString("InvalidFormatError", R.string.InvalidFormatError),
                LocaleController.formatString("InvalidCustomEmojiTypeface", R.string.InvalidCustomEmojiTypeface),
                resourcesProvider
        ).show();
    }

    private void onDeleteEmojiPackPreStart(EmojiHelper.EmojiPack pack) {
        if (destroyed || listView == null || listView.adapter == null) {
            return;
        }
        notifyItemRemoved(emojiStartRow + emojiPacks.indexOf(pack));
        updateRows();
        updateEmojiSets();
        clearSelected();
    }

    private void onDeleteEmojiPackUndo(EmojiHelper.EmojiPack pack) {
        if (destroyed || listView == null || listView.adapter == null) {
            return;
        }
        updateRows();
        notifyItemInserted(emojiStartRow + EmojiHelper.getInstance().getEmojiPacksInfo().indexOf(pack));
        updateEmojiSets();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        fileOperationId++;
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        if (chatAttachAlert != null) {
            chatAttachAlert.dismiss();
            chatAttachAlert = null;
        }
        EmojiHelper.getInstance().dismissEmojiPackBulletin();
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasSelected()) {
            if (invoked) clearSelected();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private static class DeleteEmojiPackBulletinAction implements EmojiHelper.OnBulletinAction {
        private final WeakReference<LawxEmojiSettingsActivity> activityRef;
        private final EmojiHelper.EmojiPack pack;

        private DeleteEmojiPackBulletinAction(LawxEmojiSettingsActivity activity, EmojiHelper.EmojiPack pack) {
            activityRef = new WeakReference<>(activity);
            this.pack = pack;
        }

        @Override
        public void onPreStart() {
            LawxEmojiSettingsActivity activity = activityRef.get();
            if (activity != null) {
                activity.onDeleteEmojiPackPreStart(pack);
            }
        }

        @Override
        public void onUndo() {
            LawxEmojiSettingsActivity activity = activityRef.get();
            if (activity != null) {
                activity.onDeleteEmojiPackUndo(pack);
            }
        }
    }

    private static class EmojiSetCellFactory extends UItem.UItemFactory<EmojiSetCell> {
        static {
            setup(new EmojiSetCellFactory());
        }

        @Override
        public EmojiSetCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new EmojiSetCell(context, true, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            var cell = (EmojiSetCell) view;
            var pack = cell.getPack();
            var newPack = (EmojiHelper.EmojiPack) item.object;
            cell.setChecked(item.checked, pack != null);
            cell.setSelected((boolean) item.object2, pack != null);
            cell.setData(newPack, pack != null, divider);
        }

        public static UItem of(int id, EmojiHelper.EmojiPack pack, boolean checked, boolean selected) {
            var item = UItem.ofFactory(EmojiSetCellFactory.class);
            item.id = id;
            item.checked = checked;
            item.object2 = selected;
            item.object = pack;
            return item;
        }
    }

    protected static class TextCreationCellFactory extends UItem.UItemFactory<CreationTextCell> {
        static {
            setup(new TextCreationCellFactory());
        }

        @Override
        public CreationTextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new CreationTextCell(context, 71, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            var textCell = (CreationTextCell) view;
            textCell.setTextAndIcon(item.text, item.drawable, divider);
        }

        public static UItem of(int id, CharSequence title, Drawable icon) {
            var item = UItem.ofFactory(TextCreationCellFactory.class);
            item.id = id;
            item.text = title;
            item.drawable = icon;
            return item;
        }
    }

}
