package com.gmail.matejpesl1.mimi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class Notifications {
    private static int currId = 250165565;

    private static final String DEFAULT_CHANNEL_ID = "mimibazar_updates_default_channel";
    private static boolean defaultChannelRegistered = false;

    private Notifications() {}

    private static void RegisterDefaultNotificationChannel(Context context) {
        registerChannel(context, createDefaultNotificationChannel());
    }

    public static void PostDefaultNotification(Context context, String title, String text) {
        if (!defaultChannelRegistered) {
            defaultChannelRegistered = true;
            Notifications.RegisterDefaultNotificationChannel(context);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Notifications.DEFAULT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_background)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(currId++, builder.build());
    }

    private static void registerChannel(Context context, NotificationChannel channel) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private static NotificationChannel createDefaultNotificationChannel() {
        CharSequence name = "Default Channel";
        String description = "Default Channel for Mimibazar Updates";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        return channel;
    }
}
