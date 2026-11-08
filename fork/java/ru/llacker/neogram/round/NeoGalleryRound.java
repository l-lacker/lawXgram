package ru.llacker.neogram.round;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ru.llacker.neogram.NeoStrings;
import ru.llacker.neogram.core.round.RoundGeometry;
import ru.llacker.neogram.core.round.RoundSpec;

/**
 * "Send as video message" for a video from the gallery (patch feature/gallery-round).
 *
 * <p>The send menus only <em>mark</em> the video: its {@link VideoEditedInfo} gets
 * {@code roundVideo = true}, nothing else. Stock code never hands such an info to
 * {@code prepareSendingMedia} (camera video messages go straight to {@code prepareSendingVideo}),
 * so {@link #routeRounds} can take marked videos out of the album there and, on a background
 * thread, turn the user's edits into a video message: the central square of their crop, at most a
 * minute from their trim start, the round-video side, bitrate and frame rate of the recorder
 * settings. Then the stock {@code prepareSendingVideo} converts and sends it like a camera video
 * message (round_message attribute, blurred thumbnail, no caption, no grouping), with the stock
 * converter: crop, filters and drawings included.</p>
 */
public final class NeoGalleryRound {

    private static boolean armed;

    private NeoGalleryRound() {
    }

    // ---- PhotoViewer --------------------------------------------------------------------------

    /**
     * Send long-press menu of PhotoViewer. {@code item} is the current item when it is a video the
     * viewer can convert, sent alone and not being edited, else null.
     */
    @MainThread
    public static void addViewerItem(@NonNull ItemOptions options, @Nullable ChatActivity chat, @Nullable Object item,
                                     @NonNull Runnable send) {
        if (isGalleryVideo(item) && canSendRound(chat)) {
            options.add(R.drawable.input_video, NeoStrings.get(R.string.NeoSendAsRound), () -> {
                armed = true;
                try {
                    send.run();
                } finally {
                    armed = false;
                }
            });
        }
    }

    /** PhotoViewer.sendPressed, right after the edited info of the current video was built. */
    @Nullable
    public static VideoEditedInfo onViewerSend(@Nullable VideoEditedInfo info) {
        if (armed && info != null) {
            info.roundVideo = true;
            if (info.cropState != null) {
                // the viewer keeps editing its own crop state; the video message gets a copy
                info.cropState = info.cropState.clone();
            }
        }
        return info;
    }

    // ---- ChatAttachAlert ----------------------------------------------------------------------

    /** Send long-press menu of the attachment sheet: only for a single selected gallery video. */
    @MainThread
    public static void addAttachItem(@NonNull ItemOptions options, @Nullable ChatActivity chat,
                                     @Nullable Map<Object, Object> selected, @NonNull Runnable send) {
        if (selected == null || selected.size() != 1 || !canSendRound(chat)) {
            return;
        }
        Object value = selected.values().iterator().next();
        if (!isGalleryVideo(value)) {
            return;
        }
        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) value;
        options.add(R.drawable.input_video, NeoStrings.get(R.string.NeoSendAsRound), () -> {
            VideoEditedInfo previous = entry.editedInfo;
            VideoEditedInfo marked = markedCopy(previous, entry.path);
            entry.editedInfo = marked;
            try {
                send.run();
            } finally {
                if (entry.editedInfo == marked) {
                    entry.editedInfo = previous; // not sent: the entry is reset after a send
                }
            }
        });
    }

    /** The user's edits of an earlier viewer session (trim, crop, filters, mute) as a marked copy. */
    @NonNull
    private static VideoEditedInfo markedCopy(@Nullable VideoEditedInfo edits, @NonNull String path) {
        VideoEditedInfo info = new VideoEditedInfo();
        info.originalPath = path;
        info.startTime = -1;
        info.endTime = -1;
        if (edits != null) {
            info.startTime = edits.startTime;
            info.endTime = edits.endTime;
            info.muted = edits.muted;
            info.filterState = edits.filterState;
            info.paintPath = edits.paintPath;
            info.mediaEntities = edits.mediaEntities;
            info.cropState = edits.cropState != null ? edits.cropState.clone() : null;
        }
        info.roundVideo = true;
        return info;
    }

    /** A video file picked from the gallery (not a live photo, not a web search result). */
    private static boolean isGalleryVideo(@Nullable Object item) {
        if (!(item instanceof MediaController.PhotoEntry)) {
            return false;
        }
        MediaController.PhotoEntry entry = (MediaController.PhotoEntry) item;
        return entry.isVideo && !entry.isLivePhoto() && entry.path != null;
    }

    private static boolean canSendRound(@Nullable ChatActivity chat) {
        if (chat == null || chat.isInScheduleMode() || chat.getChatMode() != ChatActivity.MODE_DEFAULT) {
            return false;
        }
        TLRPC.Chat current = chat.getCurrentChat();
        if (current != null && !ChatObject.canSendRoundVideo(current)) {
            return false;
        }
        // paid messages confirm asynchronously; the mark would not survive until the send
        return MessagesController.getInstance(chat.getCurrentAccount()).getSendPaidMessagesStars(chat.getDialogId()) <= 0;
    }

    // ---- SendMessagesHelper -------------------------------------------------------------------

    /**
     * Start of SendMessagesHelper.prepareSendingMedia: removes the marked videos from
     * {@code media} and sends each as a video message.
     */
    @MainThread
    public static void routeRounds(@NonNull AccountInstance account, @NonNull ArrayList<SendMessagesHelper.SendingMediaInfo> media,
                                   long dialogId, @Nullable MessageObject replyToMsg, @Nullable MessageObject replyToTopMsg,
                                   @Nullable TL_stories.StoryItem storyItem, @Nullable ChatActivity.ReplyQuote quote,
                                   @Nullable MessageObject editingMessageObject, boolean notify, int scheduleDate,
                                   int scheduleRepeatPeriod, @Nullable SendMessageChatArguments chatArguments, long effectId,
                                   long payStars, long monoForumPeerId, @Nullable MessageSuggestionParams suggestionParams) {
        if (editingMessageObject != null) {
            return; // editing never turns media into a video message
        }
        List<SendMessagesHelper.SendingMediaInfo> rounds = null;
        for (Iterator<SendMessagesHelper.SendingMediaInfo> it = media.iterator(); it.hasNext(); ) {
            SendMessagesHelper.SendingMediaInfo info = it.next();
            if (info.videoEditedInfo != null && info.videoEditedInfo.roundVideo && info.path != null) {
                it.remove();
                if (rounds == null) {
                    rounds = new ArrayList<>();
                }
                rounds.add(info);
            }
        }
        if (rounds == null) {
            return;
        }
        for (SendMessagesHelper.SendingMediaInfo info : rounds) {
            final long effect = media.isEmpty() && rounds.size() == 1 ? effectId : 0;
            Utilities.globalQueue.postRunnable(() -> {
                VideoEditedInfo round = prepare(info.path, info.videoEditedInfo);
                AndroidUtilities.runOnUIThread(() -> {
                    if (round == null) {
                        BulletinFactory.global().createErrorBulletin(NeoStrings.get(R.string.NeoRoundConvertFailed)).show();
                        return;
                    }
                    SendMessagesHelper.prepareSendingVideo(account, info.path, round, null, null, dialogId, replyToMsg,
                            replyToTopMsg, storyItem, quote, null, info.ttl, null, notify, scheduleDate, scheduleRepeatPeriod,
                            false, false, null, chatArguments, effect, payStars, monoForumPeerId, suggestionParams);
                });
            });
        }
    }

    /**
     * Builds the video message: metadata of the file (createCompressionSettings), the user's
     * edits, then the square, side, time range, bitrate and frame rate. Null when the file cannot
     * be converted.
     */
    @WorkerThread
    @Nullable
    static VideoEditedInfo prepare(@NonNull String path, @NonNull VideoEditedInfo edits) {
        try {
            VideoEditedInfo info = SendMessagesHelper.createCompressionSettings(path, 0);
            if (info == null || info.originalWidth <= 0 || info.originalHeight <= 0 || info.originalDuration <= 0) {
                return null;
            }
            MediaController.CropState crop = edits.cropState != null && edits.cropState.useMatrix == null
                    ? edits.cropState.clone() : new MediaController.CropState();
            info.rotationValue = Math.floorMod(info.rotationValue + crop.transformRotation, 360);
            info.resultWidth = info.originalWidth;
            info.resultHeight = info.originalHeight;

            RoundGeometry.Square square = RoundGeometry.square(info.resultWidth, info.resultHeight, info.rotationValue,
                    crop.cropPw, crop.cropPh, 1f, preferredSide());
            crop.cropPw = square.cropPw;
            crop.cropPh = square.cropPh;
            crop.transformWidth = square.side;
            crop.transformHeight = square.side;
            info.cropState = crop;

            long[] range = RoundGeometry.trim(edits.startTime, edits.endTime, info.originalDuration);
            info.startTime = range[0] > 0 ? range[0] : -1;
            info.endTime = range[1] < info.originalDuration ? range[1] : -1;
            info.estimatedDuration = Math.max(1L, (range[1] - range[0]) / 1000L);

            info.muted = edits.muted;
            info.filterState = edits.filterState;
            info.paintPath = edits.paintPath;
            info.mediaEntities = edits.mediaEntities;

            int sourceBitrate = MediaController.getVideoBitrate(path);
            int bitrate = SharedSettings.roundVideoVideoBitrate.get();
            if (sourceBitrate > 0) {
                bitrate = Math.min(bitrate, sourceBitrate);
            }
            // -1 is how a muted video survives the "ve" serialization; the converter then uses its default
            info.bitrate = info.muted ? -1 : bitrate;
            int frameRate = SharedSettings.roundVideoFrameRate.get() == RoundVideoSession.FrameRate.FPS_60 ? 60 : 30;
            info.framerate = info.framerate > 0 ? Math.min(info.framerate, frameRate) : frameRate;
            info.estimatedSize = Math.max(1L, RoundSpec.estimatedBytes(Math.max(bitrate, 0),
                    info.muted ? 0 : RoundSpec.AUDIO_BITRATE, info.estimatedDuration));
            info.roundVideo = true;
            return info;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** The side chosen for recorded video messages (Neogram's 560/624 included). */
    private static int preferredSide() {
        return SharedSettings.roundVideoOutputResolution.get().getSize();
    }
}
