package com.gmail.matejpesl1.mimi;

import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.ThreadLocalRandom;

public class ProgressNotification {
    private static final String TAG = ProgressNotification.class.getSimpleName();
    public final int max;
    private final int id = ThreadLocalRandom.current().nextInt(0, 5_000_000);
    private final NotificationCompat.Builder builder;
    private final NotificationManagerCompat manager;

    public ProgressNotification(String title, String text, int max, Context context) {
        this.max = max;
        builder = Notifications.getProgressNotificationBuilder(context, title, text, Notifications.Channel.UPDATE_PROGRESS);
        manager = NotificationManagerCompat.from(context);

        builder.setProgress(max, 0, false);
        manager.notify(id, builder.build());
    }

    public void updateProgress(int progress, String text) {
        builder.setProgress(max, progress, false);
        builder.setContentText(text);
        manager.notify(id, builder.build());
    }

    public void cancel() {
        manager.cancel(id);
    }
}
