package com.gmail.matejpesl1.mimi.services;

import static com.gmail.matejpesl1.mimi.utils.InternetUtils.getMobileDataStateRoot;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isWifiEnabled;
import static com.gmail.matejpesl1.mimi.utils.Utils.getBooleanPref;
import static com.gmail.matejpesl1.mimi.utils.Utils.getLongPref;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.gmail.matejpesl1.mimi.AppUpdateManager;
import com.gmail.matejpesl1.mimi.Notifications;
import com.gmail.matejpesl1.mimi.QueuedUpdateWorker;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateArranger;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class UpdateService extends IntentService {
    private static final String TAG = UpdateService.class.getSimpleName();
    private static final String RETRY_UPDATE_WORKER_TAG = "RetryUpdateWorker";
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";
    public static final int UPDATE_APPROX_MAX_DURATION_MILLIS = 300000; // 5 minutes.
    private static final String PREF_RUNNING = "update_service_running";
    private static final String PREF_LAST_START_TIME_MILLIS = "update_service_last_start_time";

    public UpdateService() {
        super(TAG);
    }

    public static void startUpdateImmediately(Context context) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(ACTION_UPDATE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // To prevent a complete block if the app was killed during updating and Running flag would not be set back to false.
        long timeSinceLastUpdate = System.currentTimeMillis() - getLongPref(this, PREF_LAST_START_TIME_MILLIS, 0);
        if (getBooleanPref(this, PREF_RUNNING, false) && timeSinceLastUpdate <= UPDATE_APPROX_MAX_DURATION_MILLIS) {
            Log.w(TAG, "Update Service is already running, returning.");
            return;
        }

        Utils.writePref(this, PREF_RUNNING, true);
        Utils.writePref(this, PREF_LAST_START_TIME_MILLIS, System.currentTimeMillis());

        PowerManager.WakeLock wakelock = acquireWakelock(5);
        Log.i(TAG, "Update Service intent received.");

        if (UpdateServiceAlarmManager.isRegistered(this))
            UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);

        InternetUtils.DataState prevDataState = getMobileDataStateRoot();
        boolean prevWifiEnabled = isWifiEnabled(this);

        Log.i(TAG, String.format("prev data: %s | prev wifi: %s", prevDataState.toString(), prevWifiEnabled));

        boolean hasInternet = InternetUtils.tryAssertHasInternet(
                this, prevDataState, prevWifiEnabled,
                getBooleanPref(this, R.string.setting_allow_wifi_change_key, true),
                getBooleanPref(this, R.string.setting_allow_data_change_key, true));

        // Execute only if internet connection could be established.
        if (hasInternet) {
            Log.i(TAG, "Internet connection could be established, executing Updater.");
            if (AppUpdateManager.isUpdateAvailable(this))
                AppUpdateManager.downloadApkAsync(this, null);

            new UpdateArranger(this).arrangeAndUpdate();

            if (AppUpdateManager.isUpdateAvailable(this))
                Notifications.postNotification(this, "Dostupná aktualizace!",
                        "Dostupná nová verze Mimibazar Aktualizací", Notifications.Channel.APP_UPDATE);
        } else {
            boolean retry = getBooleanPref(this, R.string.setting_update_additionally_key, true);
            Log.i(TAG, "Internet connection could not be established. Retry allowed: " + retry);

            Notifications.postNotification(this, R.string.mimibazar_cannot_update,
                    "Nelze získat internetové připojení." +
                            (retry ? " Aktualizace proběhne, až bude dostupné." : ""),
                    Notifications.Channel.ERROR);

            if (retry)
                enqueueUpdateRetryWorker(this);
        }

        InternetUtils.setWifiEnabledRoot(this, prevWifiEnabled);
        InternetUtils.setDataEnabledRoot(prevDataState == InternetUtils.DataState.ENABLED);
        AppUpdateManager.waitForDownloadThreadIfExists();
        Utils.writePref(this, PREF_RUNNING, false);
        wakelock.release();
    }

    private static void enqueueUpdateRetryWorker(Context context) {
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();

        final androidx.work.WorkRequest.Builder builder = new OneTimeWorkRequest.Builder(QueuedUpdateWorker.class)
                .setConstraints(constraints)
                .addTag(RETRY_UPDATE_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 120/*Min. 10 secs*/, TimeUnit.SECONDS);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setInitialDelay(Duration.ofSeconds(10));

        WorkManager.getInstance(context).enqueue(builder.build());
    }

    private PowerManager.WakeLock acquireWakelock(int minutes) {
        PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MimibazarUpdater::UpdateServiceWakeLock");

        wakeLock.acquire(minutes * 60 * 1000L);
        return wakeLock;
    }

}