package com.gmail.matejpesl1.mimi.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.gmail.matejpesl1.mimi.Notifications;
import com.gmail.matejpesl1.mimi.QueuedUpdateWorker;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gmail.matejpesl1.mimi.utils.InternetUtils.getMobileDataState;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isWifiEnabled;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.revertToInitialState;
import static com.gmail.matejpesl1.mimi.utils.Utils.*;

public class UpdateService extends IntentService {
    private static final String TAG = "UpdateService";
    private static final String RETRY_UPDATE_WORKER_TAG = "RetryUpdateWorker";
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";

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
        Log.i(TAG, "Update Service intent received.");

        PowerManager.WakeLock wakelock = acquireWakelock(5);
        if (UpdateServiceAlarmManager.isRegistered(this))
            UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);

        InternetUtils.DataState prevMobileDataState = getMobileDataState();
        boolean prevWifiEnabled = isWifiEnabled(this);

        Log.i(TAG, String.format("prev data: %s | prev wifi: %s", prevMobileDataState.toString(), prevWifiEnabled));

        boolean hasInternet = InternetUtils.tryAssertHasInternet(this,
                prevMobileDataState, prevWifiEnabled,
                getBooleanPref(this, R.string.setting_allow_wifi_change_key, true),
                getBooleanPref(this, R.string.setting_allow_data_change_key, true));
        // Execute only if internet connection could be established.
        if (hasInternet) {
            Log.i(TAG, "Internet connection could be established, executing Updater.");
            new Updater(this).update();
        } else {
            boolean retry = getBooleanPref(this, R.string.setting_update_additionally_key, true);

            Log.i(TAG, "Internet connection could not be established. Retry allowed: " + retry);

            Notifications.postNotification(this, R.string.mimibazar_cannot_update,
                    "Nelze získat internetové připojení." +
                            (retry ? " Aktualizace proběhne až bude dostupné." : ""),
                    Notifications.Channel.ERROR);

            if (retry) {
                Log.i(TAG, "Enqueing worker to retry the update when connection is available.");
                enqueueUpdateRetryWorker(this);
            }
        }

        // TODO: Maybe add auto-update or update notification
        revertToInitialState(this, prevMobileDataState, prevWifiEnabled);
        wakelock.release();
    }

    private static void enqueueUpdateRetryWorker(Context context) {
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();

        final WorkRequest updateWorkRequest = new OneTimeWorkRequest.Builder(QueuedUpdateWorker.class)
                .setInitialDelay(Duration.ofSeconds(10))
                .setConstraints(constraints)
                .addTag(RETRY_UPDATE_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 120/*Min. 10 secs*/, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context).enqueue(updateWorkRequest);
    }

    private PowerManager.WakeLock acquireWakelock(int minutes) {
        PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MimibazarUpdater::UpdateServiceWakeLock");

        wakeLock.acquire(minutes * 60 * 1000L);
        return wakeLock;
    }

}