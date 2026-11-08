package ru.llacker.neogram.chat;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import ru.llacker.neogram.NeoStrings;

/** Technical details of a message ("Details" in the message menu); tapping a row copies its value. */
public class NeoMessageDetails extends UniversalFragment {

    private static final int MAXIMUM_SHOWN_LENGTH = 48;

    private final MessageObject message;
    private final ArrayList<String[]> rows = new ArrayList<>();

    public NeoMessageDetails(@NonNull MessageObject message) {
        this.message = message;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected CharSequence getTitle() {
        return NeoStrings.get(R.string.NeoMessageDetails);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        TLRPC.Message owner = message.messageOwner;
        MessagesController controller = getMessagesController();

        items.add(UItem.asHeader(NeoStrings.get(R.string.NeoDetailsMessage)));
        add(items, "ID", String.valueOf(message.getId()));
        add(items, LocaleController.getString(R.string.NeoDetailsDate), date(owner.date));
        if (owner.edit_date != 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsEdited), date(owner.edit_date));
        }
        if (owner.grouped_id != 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsAlbum), String.valueOf(owner.grouped_id));
        }
        if (owner.reply_to != null && owner.reply_to.reply_to_msg_id != 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsReplyTo), String.valueOf(owner.reply_to.reply_to_msg_id));
        }
        if (owner.views > 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsViews), String.valueOf(owner.views));
        }
        if (owner.forwards > 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsForwards), String.valueOf(owner.forwards));
        }
        if (!TextUtils.isEmpty(message.messageText) && message.type == MessageObject.TYPE_TEXT) {
            add(items, LocaleController.getString(R.string.NeoDetailsText), message.messageText.toString());
        }

        items.add(UItem.asHeader(NeoStrings.get(R.string.NeoDetailsPeers)));
        long dialogId = message.getDialogId();
        add(items, LocaleController.getString(R.string.NeoDetailsChat), peerName(controller, dialogId) + " · " + dialogId);
        long fromId = message.getFromChatId();
        if (fromId != 0 && fromId != dialogId) {
            add(items, LocaleController.getString(R.string.NeoDetailsSender), peerName(controller, fromId) + " · " + fromId);
        }
        if (owner.via_bot_id != 0) {
            add(items, LocaleController.getString(R.string.NeoDetailsViaBot), peerName(controller, owner.via_bot_id) + " · " + owner.via_bot_id);
        }
        if (owner.fwd_from != null) {
            long origin = owner.fwd_from.from_id != null ? MessageObject.getPeerId(owner.fwd_from.from_id) : 0;
            String name = origin != 0 ? peerName(controller, origin) + " · " + origin : owner.fwd_from.from_name;
            if (!TextUtils.isEmpty(name)) {
                add(items, LocaleController.getString(R.string.NeoDetailsForwardedFrom), name);
            }
            if (owner.fwd_from.date != 0) {
                add(items, LocaleController.getString(R.string.NeoDetailsForwardedDate), date(owner.fwd_from.date));
            }
        }

        TLRPC.Document document = message.getDocument();
        if (document != null || message.isPhoto()) {
            items.add(UItem.asHeader(NeoStrings.get(R.string.NeoDetailsMedia)));
            if (document != null) {
                String fileName = FileLoader.getDocumentFileName(document);
                if (!TextUtils.isEmpty(fileName)) {
                    add(items, LocaleController.getString(R.string.NeoDetailsFileName), fileName);
                }
                add(items, LocaleController.getString(R.string.NeoDetailsFileSize), AndroidUtilities.formatFileSize(document.size));
                if (!TextUtils.isEmpty(document.mime_type)) {
                    add(items, LocaleController.getString(R.string.NeoDetailsMimeType), document.mime_type);
                }
                add(items, "DC", String.valueOf(document.dc_id));
            } else {
                TLRPC.MessageMedia media = MessageObject.getMedia(owner);
                if (media != null && media.photo != null) {
                    add(items, "DC", String.valueOf(media.photo.dc_id));
                }
            }
            File file = FileLoader.getInstance(currentAccount).getPathToMessage(owner);
            if (file != null && file.exists()) {
                add(items, LocaleController.getString(R.string.NeoDetailsFilePath), file.getAbsolutePath());
            }
        }

        if (owner.restriction_reason != null && !owner.restriction_reason.isEmpty()) {
            items.add(UItem.asHeader(NeoStrings.get(R.string.NeoRestrictionReason)));
            for (TLRPC.RestrictionReason reason : owner.restriction_reason) {
                add(items, reason.platform + " · " + reason.reason, reason.text);
            }
        }
        items.add(UItem.asShadow(NeoStrings.get(R.string.NeoDetailsCopyHint)));
    }

    private void add(@NonNull ArrayList<UItem> items, @NonNull String title, @Nullable String value) {
        if (value == null) {
            return;
        }
        rows.add(new String[]{title, value});
        String shown = value.replace('\n', ' ');
        if (shown.length() > MAXIMUM_SHOWN_LENGTH) {
            shown = shown.substring(0, MAXIMUM_SHOWN_LENGTH) + "…";
        }
        items.add(UItem.asButton(rows.size() - 1, title, shown));
    }

    @NonNull
    private static String date(int seconds) {
        return LocaleController.getInstance().getFormatterStats().format(new Date(seconds * 1000L));
    }

    @NonNull
    private static String peerName(@NonNull MessagesController controller, long peerId) {
        if (peerId > 0) {
            TLRPC.User user = controller.getUser(peerId);
            return user != null ? UserObject.getUserName(user) : String.valueOf(peerId);
        }
        if (DialogObject.isEncryptedDialog(peerId)) {
            return LocaleController.getString(R.string.SecretChat);
        }
        TLRPC.Chat chat = controller.getChat(-peerId);
        return chat != null && chat.title != null ? chat.title : String.valueOf(peerId);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 0 || item.id >= rows.size()) {
            return;
        }
        AndroidUtilities.addToClipboard(rows.get(item.id)[1]);
        if (AndroidUtilities.shouldShowClipboardToast()) {
            BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
