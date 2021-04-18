package com.gmail.matejpesl1.mimi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.ThreadLocalRandom;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

public class Notifications {
    private Notifications() {}

    // I miss C#'s structs and properties... This is all so much boilerplate.
    public enum Channel {
        DEFAULT(new DefaultChannel()), ERROR(new ErrorChannel());

        protected final IChannel channel;
        Channel(IChannel channel) {
            this.channel = channel;
        }
    }

    interface IChannel {
        String getId();
        int getNameRes();
        int getDescRes();
        int getImportance();
    }

    private static class DefaultChannel implements IChannel {
        public String getId() { return "mimibazar_updates_default_channel"; }
        public int getNameRes() { return R.string.channel_default_name; }
        public int getDescRes() { return R.string.channel_default_desc; }
        public int getImportance() { return IMPORTANCE_DEFAULT; }
    }

    private static class ErrorChannel implements IChannel {
        public String getId() { return "mimibazar_updates_error_channel"; }
        public int getNameRes() { return R.string.channel_error_name; }
        public int getDescRes() { return R.string.channel_error_desc; }
        public int getImportance() { return IMPORTANCE_HIGH; }
    }

    // TODO: You ended here.
    public static void PostNotification(Context context, String title, String text, Channel channelType) {
        IChannel channel = channelType.channel;
        createNotificationChannel(context, channel);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channel.getId())
                        .setSmallIcon(R.drawable.ic_launcher_background)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE), builder.build());
    }

    private static void createNotificationChannel(Context context, IChannel channel) {
        CharSequence name = context.getResources().getString(channel.getNameRes());
        String description = context.getResources().getString(channel.getDescRes());

        NotificationChannel newChannel = new NotificationChannel(channel.getId(), name, channel.getImportance());
        newChannel.setDescription(description);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(newChannel);
    }
}
