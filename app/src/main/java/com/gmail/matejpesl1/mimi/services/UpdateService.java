package com.gmail.matejpesl1.mimi.services;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
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
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gmail.matejpesl1.mimi.utils.InternetUtils.getMobileDataState;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isConnectionAvailable;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isWifiEnabled;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.pingConnection;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.revertToInitialState;

public class UpdateService extends IntentService {
    private static final String TAG = "UpdateService";
    private static final String PREF_ALLOW_DATA_CHANGE = "Allow Mobile Data Change";
    private static final String PREF_ALLOW_WIFI_CHANGE = "Allow Wifi Change";
    private static final String PREF_RETRY_WHEN_INTERNET_AVAILABLE = "Retry When Internet Available";
    private static final String RETRY_UPDATE_WORKER_TAG = "RetryUpdateWorker";
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";

    public UpdateService() {
        super(TAG);
    }

    public static void setAllowDataChange(Context context, boolean allow) {
        Utils.writePref(context, PREF_ALLOW_DATA_CHANGE, allow + "");
    }

    public static boolean getAllowDataChange(Context context) {
        return Boolean.parseBoolean(Utils.getPref(context, PREF_ALLOW_DATA_CHANGE, "true"));
    }

    public static void setAllowWifiChange(Context context, boolean allow) {
        Utils.writePref(context, PREF_ALLOW_WIFI_CHANGE, allow + "");
    }

    public static boolean getAllowWifiChange(Context context) {
        return Boolean.parseBoolean(Utils.getPref(context, PREF_ALLOW_WIFI_CHANGE, "true"));
    }

    public static void setRetryWhenInternetAvailable(Context context, boolean allow) {
        Utils.writePref(context, PREF_RETRY_WHEN_INTERNET_AVAILABLE, allow + "");
    }

    public static boolean getRetryWhenInternetAvailable(Context context) {
        return Boolean.parseBoolean(Utils.getPref(context, PREF_RETRY_WHEN_INTERNET_AVAILABLE, "true"));
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

        boolean hasInternet = InternetUtils.tryAssertHasInternet(this, prevMobileDataState,
                prevWifiEnabled, getAllowWifiChange(this), getAllowDataChange(this));
        // Execute only if internet connection could be established.
        if (hasInternet) {
            Log.i(TAG, "Internet connection could be established, executing Updater.");
            new Updater(this).update();
        } else {
            boolean retry = getRetryWhenInternetAvailable(this);

            Log.i(TAG, "Internet connection could not be established. Retry allowed: " + retry);

            Notifications.PostNotification(this,
                    getResources().getString(R.string.cannot_update_mimibazar),
                    "Nelze získat internetové připojení." +
                            (retry ? " Aktualizace proběhne až bude dostupné." : ""),
                    Notifications.Channel.ERROR);

            if (retry) {
                Log.i(TAG, "Enqueing worker to retry the update when connection is available.");
                enqueueUpdateRetryWorker(this);
            }
        }

        // TODO: Maybe add auto-update?
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