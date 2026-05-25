package ru.llacker.lawxgram.helpers;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;

import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranscribeButton;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MessageHelper extends BaseController {

    private static final MessageHelper[] Instance = new MessageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    private static final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();
    private static final SpannableStringBuilder[] spannedStrings = new SpannableStringBuilder[5];
    private static final long DELETE_HISTORY_SEARCH_TIMEOUT = 60_000L;
    private static final int QR_SCAN_MAX_BITMAP_SIDE = 2048;

    private final ArrayDeque<DeleteHistoryJob> deleteHistoryQueue = new ArrayDeque<>();
    private boolean deleteHistoryRunning;

    public MessageHelper(int num) {
        super(num);
    }

    private static String formatTime(int timestamp) {
        return LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(timestamp * 1000L)), LocaleController.getInstance().getFormatterDayWithSeconds().format(new Date(timestamp * 1000L)));
    }

    public static CharSequence getTimeHintText(MessageObject messageObject) {
        var text = new SpannableStringBuilder();
        if (spannedStrings[3] == null) {
            spannedStrings[3] = new SpannableStringBuilder("\u200B");
            spannedStrings[3].setSpan(new ColoredImageSpan(Theme.chat_timeHintSentDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.append(spannedStrings[3]);
        text.append(' ');
        text.append(formatTime(messageObject.messageOwner.date));
        if (messageObject.messageOwner.edit_date != 0) {
            text.append("\n");
            if (spannedStrings[1] == null) {
                spannedStrings[1] = new SpannableStringBuilder("\u200B");
                spannedStrings[1].setSpan(new ColoredImageSpan(Theme.chat_editDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[1]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.edit_date));
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.date != 0) {
            text.append("\n");
            if (spannedStrings[4] == null) {
                spannedStrings[4] = new SpannableStringBuilder("\u200B");
                var span = new ColoredImageSpan(Theme.chat_timeHintForwardDrawable);
                span.setSize(AndroidUtilities.dp(12));
                spannedStrings[4].setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[4]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.fwd_from.date));
        }
        return text;
    }

    public static CharSequence createBlockedString(MessageObject messageObject) {
        if (spannedStrings[2] == null) {
            spannedStrings[2] = new SpannableStringBuilder("\u200B");
            spannedStrings[2].setSpan(new ColoredImageSpan(Theme.chat_blockDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        var spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder
                .append(spannedStrings[2])
                .append(' ')
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createEditedString(MessageObject messageObject) {
        if (spannedStrings[1] == null) {
            spannedStrings[1] = new SpannableStringBuilder("\u200B");
            var span = new ColoredImageSpan(Theme.chat_editDrawable);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                span.setContentDescription(LocaleController.getString(R.string.EditedMessage));
            }
            spannedStrings[1].setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        var spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder
                .append(spannedStrings[1])
                .append(' ')
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createTranslateString(MessageObject messageObject) {
        var fromLanguage = messageObject.messageOwner.originalLanguage;
        var toLanguage = messageObject.messageOwner.translatedToLanguage;
        if ("und".equals(fromLanguage) || TextUtils.isEmpty(fromLanguage) || TextUtils.isEmpty(toLanguage)) {
            return LocaleController.getString(R.string.Translated) + " " + LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000);
        }
        if (spannedStrings[0] == null) {
            spannedStrings[0] = new SpannableStringBuilder("\u200B");
            spannedStrings[0].setSpan(new ColoredImageSpan(Theme.chat_arrowDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        Locale from = Locale.forLanguageTag(fromLanguage);
        Locale to = Locale.forLanguageTag(toLanguage);
        var spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder
                .append(!TextUtils.isEmpty(from.getScript()) ? HtmlCompat.fromHtml(from.getDisplayScript(), HtmlCompat.FROM_HTML_MODE_LEGACY) : from.getDisplayName())
                .append(' ')
                .append(spannedStrings[0])
                .append(' ')
                .append(!TextUtils.isEmpty(to.getScript()) ? HtmlCompat.fromHtml(to.getDisplayScript(), HtmlCompat.FROM_HTML_MODE_LEGACY) : to.getDisplayName())
                .append(' ')
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            var context = ApplicationLoader.applicationContext;
            var clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            var uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            var clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String getTextOrBase64(byte[] data) {
        try {
            return utf8Decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return Base64.encodeToString(data, Base64.NO_PADDING | Base64.NO_WRAP);
        }
    }

    public void clearMessageFiles(MessageObject messageObject, Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                var files = getFilesToMessage(messageObject);
                for (File file : files) {
                    if (file.exists() && !file.delete()) {
                        file.deleteOnExit();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageObject.checkMediaExistance();
            AndroidUtilities.runOnUIThread(done);
        });
    }

    public ArrayList<File> getFilesToMessage(MessageObject messageObject) {
        ArrayList<File> files = new ArrayList<>();
        files.add(new File(messageObject.messageOwner.attachPath));
        files.add(getFileLoader().getPathToMessage(messageObject.messageOwner));
        var document = messageObject.getDocument();
        if (document != null) {
            files.add(getFileLoader().getPathToAttach(document, false));
            files.add(getFileLoader().getPathToAttach(document, true));
        }
        var media = messageObject.messageOwner.media;
        if (media != null && !media.alt_documents.isEmpty()) {
            media.alt_documents.forEach(doc -> {
                files.add(getFileLoader().getPathToAttach(doc, false));
                files.add(getFileLoader().getPathToAttach(doc, true));
            });
        }
        return files;
    }

    public void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        var path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            addFileToClipboard(new File(path), callback);
        }
    }

    private MessageObject getTargetMessageObjectFromGroup(MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        for (MessageObject object : selectedObjectGroup.messages) {
            if (!TextUtils.isEmpty(object.messageOwner.message)) {
                if (messageObject != null) {
                    messageObject = null;
                    break;
                } else {
                    messageObject = object;
                }
            }
        }
        return messageObject;
    }

    public String getMessagePlainText(MessageObject messageObject) {
        String message;
        if (messageObject.isPoll()) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
            for (TLRPC.PollAnswer answer : poll.answers) {
                pollText.append("\n\uD83D\uDD18 ");
                pollText.append(answer.text.text);
            }
            message = pollText.toString();
        } else if (messageObject.isVoiceTranscriptionOpen()) {
            message = messageObject.messageOwner.voiceTranscription;
        } else {
            message = messageObject.messageOwner.message;
        }
        return message;
    }

    public MessageObject getMessageForTranslate(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        if (selectedObjectGroup != null && !selectedObjectGroup.isDocuments) {
            messageObject = getTargetMessageObjectFromGroup(selectedObjectGroup);
        } else if (selectedObject.isPoll()) {
            messageObject = selectedObject;
        } else if (selectedObject.isVoiceTranscriptionOpen() && !TextUtils.isEmpty(selectedObject.messageOwner.voiceTranscription) && !TranscribeButton.isTranscribing(selectedObject)) {
            messageObject = selectedObject;
        } else if (!selectedObject.isVoiceTranscriptionOpen() && !TextUtils.isEmpty(selectedObject.messageOwner.message) && !isLinkOrEmojiOnlyMessage(selectedObject)) {
            messageObject = selectedObject;
        }
        if (messageObject != null && messageObject.translating) {
            return null;
        }
        if (messageObject != null && messageObject.translated && !messageObject.manually) {
            return null;
        }
        return messageObject;
    }

    public static boolean isLinkOrEmojiOnlyMessage(MessageObject messageObject) {
        return messageObject.getEmojiOnlyCount() > 0 || isLinkOnlyMessage(messageObject.messageOwner.message, messageObject.messageOwner.entities);
    }

    public static boolean isLinkOnlyMessage(String message, ArrayList<TLRPC.MessageEntity> entities) {
        if (message == null) {
            return false;
        }
        if (entities != null) {
            for (TLRPC.MessageEntity entity : entities) {
                if (entity instanceof TLRPC.TL_messageEntityBotCommand ||
                        entity instanceof TLRPC.TL_messageEntityEmail ||
                        entity instanceof TLRPC.TL_messageEntityUrl ||
                        entity instanceof TLRPC.TL_messageEntityMention ||
                        entity instanceof TLRPC.TL_messageEntityCashtag ||
                        entity instanceof TLRPC.TL_messageEntityHashtag ||
                        entity instanceof TLRPC.TL_messageEntityBankCard ||
                        entity instanceof TLRPC.TL_messageEntityPhone) {
                    if (entity.offset == 0 && entity.length == message.length()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public MessageObject getMessageForRepeat(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        if (selectedObjectGroup != null && !selectedObjectGroup.isDocuments) {
            messageObject = getTargetMessageObjectFromGroup(selectedObjectGroup);
        } else if (!TextUtils.isEmpty(selectedObject.messageOwner.message) || selectedObject.isAnyKindOfSticker()) {
            messageObject = selectedObject;
        }
        return messageObject;
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                return null;
            }
        }
        return path;
    }

    public void saveStickerToGallery(Activity activity, MessageObject messageObject, Utilities.Callback<Uri> callback) {
        saveStickerToGallery(activity, getPathToMessage(messageObject), messageObject.isVideoSticker(), messageObject.isAnimatedSticker(), callback);
    }

    public static void saveStickerToGallery(Activity activity, TLRPC.Document document, Utilities.Callback<Uri> callback) {
        String path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true).toString();
        File temp = new File(path);
        if (!temp.exists()) {
            return;
        }
        saveStickerToGallery(activity, path, MessageObject.isVideoSticker(document), MessageObject.isAnimatedStickerDocument(document, true), callback);
    }

    private static void saveStickerToGallery(Activity activity, String path, boolean video, boolean animated, Utilities.Callback<Uri> callback) {
        if (path == null) return;
        var activityRef = new WeakReference<>(activity);
        Context saveContext = ApplicationLoader.applicationContext;
        Utilities.Callback<Uri> safeCallback = callback == null ? null : uri -> {
            Activity callbackActivity = activityRef.get();
            if (callbackActivity == null || callbackActivity.isFinishing() || Build.VERSION.SDK_INT >= 17 && callbackActivity.isDestroyed()) {
                return;
            }
            callback.run(uri);
        };
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (video || animated) {
                    StickerHelper.convertStickerFormat(path, animated, path1 -> Utilities.globalQueue.postRunnable(() ->
                            MediaController.saveFile(path1, saveContext, 0, null, null, safeCallback)));
                } else {
                    var image = BitmapFactory.decodeFile(path);
                    if (image != null) {
                        try {
                            var file = new File(path.endsWith(".webp") ? path.replace(".webp", ".png") : path + ".png");
                            try (var stream = new FileOutputStream(file)) {
                                image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                            }
                            MediaController.saveFile(file.toString(), saveContext, 0, null, null, safeCallback);
                        } finally {
                            AndroidUtilities.recycleBitmap(image);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void readQrFromMessage(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup, ViewGroup viewGroup, Utilities.Callback<ArrayList<String>> callback, AtomicBoolean waitForQr, AtomicReference<Runnable> onQrDetectionDone) {
        waitForQr.set(true);
        ArrayList<ImageReceiver.BitmapHolder> bitmapHolders = new ArrayList<>();
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        if (selectedObjectGroup != null) {
            messageObjects.addAll(selectedObjectGroup.messages);
        } else {
            messageObjects.add(selectedObject);
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ChatMessageCell cell) {
                if (messageObjects.contains(cell.getMessageObject())) {
                    ImageReceiver.BitmapHolder bitmapHolder = cell.getPhotoImage().getBitmapSafe();
                    if (bitmapHolder != null && !bitmapHolder.isRecycled()) {
                        bitmapHolders.add(bitmapHolder);
                    } else if (bitmapHolder != null) {
                        bitmapHolder.release();
                    }
                }
            }
        }
        AtomicBoolean qrDetectionReleased = new AtomicBoolean(false);
        Runnable finishQrDetectionRunnable = () -> {
            if (!qrDetectionReleased.compareAndSet(false, true)) {
                return;
            }
            waitForQr.set(false);
            Runnable onDone = onQrDetectionDone.getAndSet(null);
            if (onDone != null) {
                onDone.run();
            }
        };
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<String> qrResults = new ArrayList<>();
            BarcodeDetector detector = QrHelper.createQrDetector();
            try {
                for (ImageReceiver.BitmapHolder bitmapHolder : bitmapHolders) {
                    Bitmap bitmap = null;
                    try {
                        if (bitmapHolder != null && !bitmapHolder.isRecycled()) {
                            bitmap = copyBitmapForQr(bitmapHolder.bitmap);
                            if (bitmap != null) {
                                qrResults.addAll(QrHelper.readQr(bitmap, detector));
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    } finally {
                        AndroidUtilities.recycleBitmap(bitmap);
                        if (bitmapHolder != null) {
                            releaseBitmapHolder(bitmapHolder);
                        }
                    }
                }
            } finally {
                QrHelper.releaseQrDetector(detector);
            }
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    callback.run(qrResults);
                } finally {
                    AndroidUtilities.cancelRunOnUIThread(finishQrDetectionRunnable);
                    finishQrDetectionRunnable.run();
                }
            });
        });
        AndroidUtilities.runOnUIThread(finishQrDetectionRunnable, 250);
    }

    private static Bitmap copyBitmapForQr(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return null;
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Bitmap.Config config = getQrBitmapConfig(bitmap);
            int maxSide = Math.max(width, height);
            if (maxSide <= QR_SCAN_MAX_BITMAP_SIDE) {
                return bitmap.copy(config, false);
            }

            float scale = QR_SCAN_MAX_BITMAP_SIDE / (float) maxSide;
            int scaledWidth = Math.max(1, Math.round(width * scale));
            int scaledHeight = Math.max(1, Math.round(height * scale));
            if (isHardwareBitmap(bitmap)) {
                Bitmap copy = null;
                try {
                    copy = bitmap.copy(config, false);
                    if (copy == null) {
                        return null;
                    }
                    Bitmap scaled = Bitmap.createScaledBitmap(copy, scaledWidth, scaledHeight, true);
                    if (scaled != copy) {
                        AndroidUtilities.recycleBitmap(copy);
                    }
                    return scaled;
                } catch (Throwable e) {
                    AndroidUtilities.recycleBitmap(copy);
                    throw e;
                }
            }

            Bitmap scaled = Bitmap.createBitmap(scaledWidth, scaledHeight, config);
            Canvas canvas = new Canvas(scaled);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            canvas.drawBitmap(bitmap, null, new Rect(0, 0, scaledWidth, scaledHeight), paint);
            return scaled;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Bitmap.Config getQrBitmapConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == Bitmap.Config.RGB_565) {
            return Bitmap.Config.RGB_565;
        }
        return Bitmap.Config.ARGB_8888;
    }

    private static boolean isHardwareBitmap(Bitmap bitmap) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE;
    }

    private static void releaseBitmapHolder(ImageReceiver.BitmapHolder bitmapHolder) {
        AndroidUtilities.runOnUIThread(() -> {
            if (bitmapHolder.getKey() == null && bitmapHolder.orientation != 0) {
                AndroidUtilities.recycleBitmap(bitmapHolder.bitmap);
            }
            bitmapHolder.release();
        });
    }

    public static MessageHelper getInstance(int num) {
        MessageHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (MessageHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MessageHelper(num);
                }
            }
        }
        return localInstance;
    }

    public void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, Theme.ResourcesProvider resourcesProvider) {
        createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, -1, resourcesProvider);
    }

    private void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, int before, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null || chat == null) {
            return;
        }

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);

        CheckBoxCell cell = before == -1 && forumTopic == null && ChatObject.isChannel(chat) && ChatObject.canUserDoAction(chat, ChatObject.ACTION_DELETE_MESSAGES) ? new CheckBoxCell(context, 1, resourcesProvider) : null;

        TextView messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell.getMeasuredHeight() + AndroidUtilities.dp(7));
                }
            }
        };
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        avatarDrawable.setInfo(chat);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        if (forumTopic != null) {
            if (forumTopic.id == 1) {
                imageView.setImageDrawable(ForumUtilities.createGeneralTopicDrawable(context, 0.75f, Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), false));
            } else {
                ForumUtilities.setTopicIcon(imageView, forumTopic, false, true, resourcesProvider);
            }
        } else {
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(LocaleController.getString(R.string.DeleteAllFromSelf));

        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        if (cell != null) {
            boolean sendAs = ChatObject.getSendAsPeerId(chat, getMessagesController().getChatFull(chat.id), true) != getUserConfig().getClientUserId();
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(LocaleController.getString(R.string.DeleteAllFromSelfAdmin), "", !ChatObject.shouldSendAnonymously(chat) && !sendAs, false);
            cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell.setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                cell1.setChecked(!cell1.isChecked(), true);
            });
        }

        if (before > 0) {
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteAllFromSelfAlertBefore, LocaleController.formatDateForBan(before))));
        } else {
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.DeleteAllFromSelfAlert)));
        }

        builder.setNeutralButton(LocaleController.getString(R.string.DeleteAllFromSelfBefore), (dialog, which) -> showBeforeDatePickerAlert(fragment, before1 -> createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, before1, resourcesProvider)));
        builder.setPositiveButton(LocaleController.getString(R.string.DeleteAll), (dialogInterface, i) -> {
            if (cell != null && cell.isChecked()) {
                showDeleteHistoryBulletin(fragment, 0, false, () -> getMessagesController().deleteUserChannelHistory(chat, getUserConfig().getCurrentUser(), null, 0), resourcesProvider);
            } else {
                deleteUserHistoryWithSearch(fragment, -chat.id, forumTopic != null ? forumTopic.id : 0, mergeDialogId, before == -1 ? getConnectionsManager().getCurrentTime() : before, (currentFragment, count, deleteAction) -> showDeleteHistoryBulletin(currentFragment, count, true, deleteAction, resourcesProvider));
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void showBeforeDatePickerAlert(BaseFragment fragment, Utilities.Callback<Integer> callback) {
        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.DeleteAllFromSelfBefore));
        builder.setItems(new CharSequence[]{
                LocaleController.formatPluralString("Days", 1),
                LocaleController.formatPluralString("Weeks", 1),
                LocaleController.formatPluralString("Months", 1),
                LocaleController.getString(R.string.UserRestrictionsCustom)
        }, (dialog1, which) -> {
            switch (which) {
                case 0:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24);
                    break;
                case 1:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 7);
                    break;
                case 2:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 30);
                    break;
                case 3: {
                    Calendar calendar = Calendar.getInstance();
                    DatePickerDialog dateDialog = new DatePickerDialog(context, (view1, year1, month, dayOfMonth1) -> {
                        TimePickerDialog timeDialog = new TimePickerDialog(context, (view11, hourOfDay, minute) -> {
                            calendar.set(year1, month, dayOfMonth1, hourOfDay, minute);
                            callback.run((int) (calendar.getTimeInMillis() / 1000));
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
                        timeDialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString(R.string.Set), timeDialog);
                        timeDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString(R.string.Cancel), (dialog3, which3) -> {
                        });
                        fragment.showDialog(timeDialog);
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

                    final DatePicker datePicker = dateDialog.getDatePicker();

                    datePicker.setMinDate(1375315200000L);
                    datePicker.setMaxDate(System.currentTimeMillis());

                    dateDialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString(R.string.Set), dateDialog);
                    dateDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString(R.string.Cancel), (dialog2, which2) -> {
                    });
                    dateDialog.setOnShowListener(dialog12 -> {
                        int count = datePicker.getChildCount();
                        for (int b = 0; b < count; b++) {
                            View child = datePicker.getChildAt(b);
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            layoutParams.width = LayoutHelper.MATCH_PARENT;
                            child.setLayoutParams(layoutParams);
                        }
                    });
                    fragment.showDialog(dateDialog);
                    break;
                }
            }
            builder.getDismissRunnable().run();
        });
        fragment.showDialog(builder.create());
    }

    public static void showDeleteHistoryBulletin(BaseFragment fragment, int count, boolean search, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        if (fragment.getParentActivity() == null) {
            if (delayedAction != null) {
                delayedAction.run();
            }
            return;
        }
        Bulletin.ButtonLayout buttonLayout;
        if (search) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.titleTextView.setText(LocaleController.getString(R.string.DeleteAllFromSelfDone));
            layout.subtitleTextView.setText(LocaleController.formatPluralString("MessagesDeletedHint", count));
            layout.setTimer();
            buttonLayout = layout;
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.textView.setText(LocaleController.getString(R.string.DeleteAllFromSelfDone));
            layout.setTimer();
            buttonLayout = layout;
        }
        buttonLayout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, resourcesProvider).setDelayedAction(delayedAction));
        Bulletin.make(fragment, buttonLayout, Bulletin.DURATION_PROLONG).show();
    }

    public void resetMessageContent(long dialogId, MessageObject messageObject, boolean translated) {
        resetMessageContent(dialogId, messageObject, translated, false);
    }

    public void resetMessageContent(long dialogId, MessageObject messageObject, boolean translated, boolean translating) {
        TLRPC.Message message = messageObject.messageOwner;

        MessageObject obj = new MessageObject(currentAccount, message, true, true);
        obj.messageBlocked = messageObject.messageBlocked;
        obj.translating = translating;
        obj.manually = translating || translated;

        replaceMessagesObject(dialogId, obj);
    }

    private void replaceMessagesObject(long dialogId, MessageObject messageObject) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(messageObject);
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList, false);
    }

    public void deleteUserHistoryWithSearch(BaseFragment fragment, final long dialogId) {
        deleteUserHistoryWithSearch(fragment, dialogId, 0, 0, -1, null);
    }

    private void deleteUserHistoryWithSearch(BaseFragment fragment, final long dialogId, int replyMessageId, final long mergeDialogId, int before, SearchMessagesResultCallback callback) {
        WeakReference<BaseFragment> fragmentRef = new WeakReference<>(fragment);
        enqueueDeleteHistoryJob(new DeleteHistoryJob(fragmentRef, dialogId, replyMessageId, mergeDialogId, before, callback));
    }

    private void enqueueDeleteHistoryJob(DeleteHistoryJob job) {
        Utilities.stageQueue.postRunnable(() -> {
            deleteHistoryQueue.add(job);
            if (!deleteHistoryRunning) {
                deleteHistoryRunning = true;
                runNextDeleteHistoryJob();
            }
        });
    }

    private void runNextDeleteHistoryJob() {
        Utilities.stageQueue.postRunnable(() -> {
            DeleteHistoryJob job = deleteHistoryQueue.poll();
            if (job == null) {
                deleteHistoryRunning = false;
                return;
            }
            searchDialogMessages(job, job.dialogId, job.replyMessageId, job.callback, success -> {
                if (success && job.mergeDialogId != 0) {
                    searchDialogMessages(job, job.mergeDialogId, 0, null, ignored -> runNextDeleteHistoryJob());
                } else {
                    runNextDeleteHistoryJob();
                }
            });
        });
    }

    private void searchDialogMessages(DeleteHistoryJob job, final long dialogId, int replyMessageId, SearchMessagesResultCallback callback, SearchMessagesFinishCallback finishCallback) {
        ArrayList<Integer> messageIds = new ArrayList<>();
        TLRPC.InputPeer peer;
        TLRPC.InputPeer fromId;
        try {
            peer = getMessagesController().getInputPeer(dialogId);
            fromId = MessagesController.getInputPeer(getUserConfig().getCurrentUser());
        } catch (Exception e) {
            FileLog.e(e);
            finishCallback.run(false);
            return;
        }
        doSearchMessages(job.fragmentRef, messageIds, peer, replyMessageId, fromId, job.before, Integer.MAX_VALUE, 0, success -> {
            if (success && !messageIds.isEmpty()) {
                ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
                final int N = messageIds.size();
                for (int i = 0; i < N; i += 100) {
                    lists.add(new ArrayList<>(messageIds.subList(i, Math.min(N, i + 100))));
                }
                Runnable deleteAction = () -> {
                    for (ArrayList<Integer> list : lists) {
                        getMessagesController().deleteMessages(list, null, null, dialogId, replyMessageId, true, 0);
                    }
                };
                AndroidUtilities.runOnUIThread(() -> {
                    BaseFragment currentFragment = job.fragmentRef.get();
                    if (callback != null && currentFragment != null && !currentFragment.isFinished && currentFragment.getParentActivity() != null) {
                        callback.run(currentFragment, messageIds.size(), deleteAction);
                    } else {
                        deleteAction.run();
                    }
                });
            }
            finishCallback.run(success);
        });
    }

    private interface SearchMessagesResultCallback {
        void run(BaseFragment fragment, int count, Runnable deleteAction);
    }

    private interface SearchMessagesFinishCallback {
        void run(boolean success);
    }

    private static class DeleteHistoryJob {
        private final WeakReference<BaseFragment> fragmentRef;
        private final long dialogId;
        private final int replyMessageId;
        private final long mergeDialogId;
        private final int before;
        private final SearchMessagesResultCallback callback;

        private DeleteHistoryJob(WeakReference<BaseFragment> fragmentRef, long dialogId, int replyMessageId, long mergeDialogId, int before, SearchMessagesResultCallback callback) {
            this.fragmentRef = fragmentRef;
            this.dialogId = dialogId;
            this.replyMessageId = replyMessageId;
            this.mergeDialogId = mergeDialogId;
            this.before = before;
            this.callback = callback;
        }
    }

    private void doSearchMessages(WeakReference<BaseFragment> fragmentRef, ArrayList<Integer> messageIds, TLRPC.InputPeer peer, int replyMessageId, TLRPC.InputPeer fromId, int before, int offsetId, long hash, SearchMessagesFinishCallback finishCallback) {
        var req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.limit = 100;
        req.q = "";
        req.offset_id = offsetId;
        req.from_id = fromId;
        req.flags |= 1;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        if (replyMessageId != 0) {
            req.top_msg_id = replyMessageId;
            req.flags |= 2;
        }
        req.hash = hash;
        SearchRequestState requestState = new SearchRequestState();
        Runnable timeoutRunnable = () -> {
            if (!requestState.tryFinish()) {
                return;
            }
            if (requestState.requestId != 0) {
                getConnectionsManager().cancelRequest(requestState.requestId, true);
            }
            finishCallback.run(false);
        };
        requestState.requestId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (!requestState.tryFinish()) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            if (response instanceof TLRPC.messages_Messages res) {
                if (response instanceof TLRPC.TL_messages_messagesNotModified || res.messages.isEmpty()) {
                    finishCallback.run(true);
                    return;
                }
                var newOffsetId = offsetId;
                for (TLRPC.Message message : res.messages) {
                    newOffsetId = Math.min(newOffsetId, message.id);
                    if (!message.out || message.post || message.date >= before) {
                        continue;
                    }
                    messageIds.add(message.id);
                }
                doSearchMessages(fragmentRef, messageIds, peer, replyMessageId, fromId, before, newOffsetId, calcMessagesHash(res.messages), finishCallback);
            } else {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BaseFragment currentFragment = fragmentRef.get();
                        if (currentFragment != null && !currentFragment.isFinished && currentFragment.getParentActivity() != null) {
                            AlertsCreator.showSimpleAlert(currentFragment, LocaleController.getString(R.string.ErrorOccurred) + "\n" + error.text);
                        }
                    });
                }
                finishCallback.run(false);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        AndroidUtilities.runOnUIThread(timeoutRunnable, DELETE_HISTORY_SEARCH_TIMEOUT);
    }

    private static class SearchRequestState {
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile int requestId;

        private boolean tryFinish() {
            return finished.compareAndSet(false, true);
        }
    }

    private long calcMessagesHash(ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long acc = 0;
        for (TLRPC.Message message : messages) {
            acc = MediaDataController.calcHash(acc, message.id);
        }
        return acc;
    }

}
