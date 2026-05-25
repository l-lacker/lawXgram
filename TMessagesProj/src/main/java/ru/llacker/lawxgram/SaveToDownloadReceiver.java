package ru.llacker.lawxgram;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SaveToDownloadReceiver extends BroadcastReceiver {
    public static final String NOTIFICATION_TAG = "MediaController";
    public static final String ACTION_CANCEL_DOWNLOAD = ApplicationLoader.getApplicationId() + ".CANCEL_SAVE_TO_DOWNLOAD";
    public static final String EXTRA_ID = ApplicationLoader.getApplicationId() + ".NOTIFICATION_ID";
    private static final ConcurrentHashMap<Integer, Runnable> callbacks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, NotificationCompat.Builder> builders = new ConcurrentHashMap<>();
    private static final AtomicInteger notificationIdStart = new AtomicInteger();
    @SuppressLint("StaticFieldLeak")
    private static NotificationManagerCompat notificationManager;

    private static NotificationManagerCompat getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        }
        return notificationManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_CANCEL_DOWNLOAD.equals(intent.getAction())) {
            int notificationId = intent.getIntExtra(EXTRA_ID, -1);
            if (notificationId >= 0) {
                var runnable = callbacks.remove(notificationId);
                try {
                    if (runnable != null) {
                        runnable.run();
                    }
                } finally {
                    cancelNotification(notificationId);
                }
            }
        }
    }

    public static int createNotificationId() {
        return notificationIdStart.getAndIncrement();
    }

    public static void showNotification(int notificationId, int count, Runnable callback) {
        Context context = ApplicationLoader.applicationContext;
        NotificationsController.checkOtherNotificationsChannel();
        var intent = new Intent(context, SaveToDownloadReceiver.class)
                .setAction(ACTION_CANCEL_DOWNLOAD)
                .putExtra(EXTRA_ID, notificationId);
        var pendingIntent = PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        var builder = new NotificationCompat.Builder(context, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setContentTitle(LocaleController.getString(R.string.AppName))
                .setTicker(LocaleController.formatPluralString("SaveToDownloadCount", count))
                .setContentText(LocaleController.formatPluralString("SaveToDownloadCount", count))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setProgress(100, 0, true)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setWhen(System.currentTimeMillis())
                .setColor(LawxConfig.getNotificationColor())
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_close_white, LocaleController.getString(R.string.Cancel), pendingIntent);
        callbacks.put(notificationId, callback);
        builders.put(notificationId, builder);
        getNotificationManager().notify(NOTIFICATION_TAG, notificationId, builder.build());
    }

    public static void updateNotification(int notificationId, int progress) {
        var builder = builders.get(notificationId);
        if (builder != null) {
            builder.setProgress(100, progress, false);
            getNotificationManager().notify(NOTIFICATION_TAG, notificationId, builder.build());
        } else {
            cancelNotification(notificationId);
        }
    }

    public static void cancelNotification(int notificationId) {
        callbacks.remove(notificationId);
        builders.remove(notificationId);
        getNotificationManager().cancel(NOTIFICATION_TAG, notificationId);
    }
}
