package com.gmail.matejpesl1.mimi;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gmail.matejpesl1.mimi.activities.MainActivity;

import java.util.concurrent.ThreadLocalRandom;

public class Notifications {
    private Notifications() {}

    // I miss C#'s structs and properties... This is all so much boilerplate.
    public enum Channel {
        DEFAULT(new DefaultChannel()), ERROR(new ErrorChannel()), APP_UPDATE(new AppUpdateChannel()),
        UPDATE_PROGRESS(new UpdateProgressChannel());

        protected final IChannel channel;
        Channel(IChannel channel) { this.channel = channel; }
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

    private static class UpdateProgressChannel implements IChannel {
        public String getId() { return "mimibazar_updates_progress_channel"; }
        public int getNameRes() { return R.string.channel_update_progress_name; }
        public int getDescRes() { return R.string.channel_update_progress_desc; }
        public int getImportance() { return IMPORTANCE_LOW; }
    }

    private static class ErrorChannel implements IChannel {
        public String getId() { return "mimibazar_updates_error_channel"; }
        public int getNameRes() { return R.string.channel_error_name; }
        public int getDescRes() { return R.string.channel_error_desc; }
        public int getImportance() { return IMPORTANCE_HIGH; }
    }

    private static class AppUpdateChannel implements IChannel {
        public String getId() { return "mimibazar_updates_app_update_channel"; }
        public int getNameRes() { return R.string.channel_app_update_name; }
        public int getDescRes() { return R.string.channel_app_update_desc; }
        public int getImportance() { return IMPORTANCE_HIGH; }
    }

    public static void postNotification(Context context, int titleRes, int textRes, Channel channelType) {
        postNotification(context, context.getString(titleRes), context.getString(textRes), channelType);
    }
    public static void postNotification(Context context, String titleRes, int textRes, Channel channelType) {
        postNotification(context, titleRes, context.getString(textRes), channelType);
    }
    public static void postNotification(Context context, int titleRes, String textRes, Channel channelType) {
        postNotification(context, context.getString(titleRes), textRes, channelType);
    }
    public static void postNotification(Context context, String title, String text, Channel channelType) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel(context, channelType.channel);

        PendingIntent appIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        Notification notification =
                new NotificationCompat.Builder(context, channelType.channel.getId())
                        .setSmallIcon(R.drawable.app_icon)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(appIntent)
                        .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(ThreadLocalRandom.current().nextInt(0, 5_000_000), notification);
    }

    public static NotificationCompat.Builder getProgressNotificationBuilder(Context context, String title, String text, Channel channelType) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel(context, channelType.channel);

        return new NotificationCompat.Builder(context, channelType.channel.getId())
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createNotificationChannel(Context context, IChannel channel) {
        CharSequence name = context.getResources().getString(channel.getNameRes());
        String description = context.getResources().getString(channel.getDescRes());

        NotificationChannel newChannel = new NotificationChannel(channel.getId(), name, channel.getImportance());
        newChannel.setDescription(description);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(newChannel);
    }
}
