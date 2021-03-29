package com.gmail.matejpesl1.mimi.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.gmail.matejpesl1.mimi.Notifications;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

import static com.gmail.matejpesl1.mimi.utils.InternetUtils.getMobileDataState;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isConnectionAvailable;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.isWifiEnabled;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.pingConnection;
import static com.gmail.matejpesl1.mimi.utils.InternetUtils.revertToInitialState;

public class UpdateService extends IntentService {
    private static final String TAG = "UpdateService";
    private static final String PREF_ALLOW_DATA_CHANGE = "Allow Mobile Data Change";
    private static final String PREF_ALLOW_WIFI_CHANGE = "Allow Wifi Change";
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";

    public UpdateService() {
        super(TAG);
    }

    public static void setAllowDataChange(Context context, boolean allow) {
        Utils.writePref(context, PREF_ALLOW_DATA_CHANGE, allow+"");
    }
    public static boolean getAllowDataChange(Context context) {
        return Boolean.parseBoolean(Utils.getPref(context, PREF_ALLOW_DATA_CHANGE, "true"));
    }
    public static void setAllowWifiChange(Context context, boolean allow) {
        Utils.writePref(context, PREF_ALLOW_WIFI_CHANGE, allow+"");
    }
    public static boolean getAllowWifiChange(Context context) {
        return Boolean.parseBoolean(Utils.getPref(context, PREF_ALLOW_WIFI_CHANGE, "true"));
    }

    public static void startUpdateImmediately(Context context) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(ACTION_UPDATE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PowerManager.WakeLock wakelock = acquireWakelock(12);
        UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);

        InternetUtils.DataState prevMobileDataState = getMobileDataState();
        boolean prevWifiEnabled = isWifiEnabled(this);

        // Execute only if internet connection could be established.
        if (tryAssertHasInternet(prevMobileDataState, prevWifiEnabled))
            new Updater().update(this);
        else
            Notifications.PostDefaultNotification(this,
                    getResources().getString(R.string.cannot_update_mimibazar),
                    getResources().getString(R.string.cannot_get_internet_connection));

        // TODO: Maybe add auto-update?
        revertToInitialState(this, prevMobileDataState, prevWifiEnabled);

        wakelock.release();
    }

    private boolean tryAssertHasInternet(InternetUtils.DataState initialDataState, boolean initialWifiEnabled) {
        // If connection is available in the current state (= without
        // any changes), return true.
        if (isConnectionAvailable())
            return true;

        if (getAllowWifiChange(this)) {
            // If connection is not available and the WIFI is off, turn it on
            // and return true if connection is now available.
            WifiManager wManager = (WifiManager) this.getSystemService(WIFI_SERVICE);

            if (!initialWifiEnabled) {
                wManager.setWifiEnabled(true);

                if (pingConnection())
                    return true;
            }

            // If connection is not available even when the WIFI is on, turn it off
            // (so that it doesn't interfere with mobile data).
            wManager.setWifiEnabled(false);

            // If data were already enabled (or unknown) and wifi too, check if the
            // data will work now without the wifi interfering and return it.
            // If data were already enabled (or unknown) but wifi not, return false - we can't
            // do anything else.
            if (initialDataState == InternetUtils.DataState.ENABLED || initialDataState == InternetUtils.DataState.UNKNOWN)
                return initialWifiEnabled ? pingConnection() : false;
        }

        if (getAllowDataChange(this)) {
            // If the data were already enabled or we don't have permission to read it (and thus
            // change it), we can't do anything else.
            if (initialDataState == InternetUtils.DataState.ENABLED || initialDataState == InternetUtils.DataState.UNKNOWN)
                return false;

            boolean set = RootUtils.setMobileDataConnection(true);
            if (set)
                return pingConnection();
        }

        return false;
    }

    private PowerManager.WakeLock acquireWakelock(int minutes) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MimibazarUpdater::UpdateServiceWakeLock");

        wakeLock.acquire(minutes * 60 * 1000L);
        return wakeLock;
    }

}