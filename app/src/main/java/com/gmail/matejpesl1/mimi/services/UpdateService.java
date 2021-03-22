package com.gmail.matejpesl1.mimi.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.gmail.matejpesl1.mimi.Notifications;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class UpdateService extends IntentService {
    private static final String PREF_ALLOW_DATA_CHANGE = "Allow Mobile Data Change";
    private static final String PREF_ALLOW_WIFI_CHANGE = "Allow Wifi Change";
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";
    private enum DataState { UNKNOWN, ENABLED, DISABLED}

    public UpdateService() {
        super("UpdateService");
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

        DataState prevMobileDataState = getMobileDataState();
        boolean prevWifiEnabled = isWifiEnabled();

        // Execute only if internet connection could be established.
        if (tryAssertHasInternet(prevMobileDataState, prevWifiEnabled))
            Updater.update(this);
        else
            Notifications.PostDefaultNotification(this, "Nelze aktualizovat Mimibazar",
                    "nelze získat internetové připojení.");

        // TODO: Maybe add auto-update?
        revertToInitialState(prevMobileDataState, prevWifiEnabled);

        wakelock.release();
    }

    private void revertToInitialState(DataState prevDataState, boolean prevWifiEnabled) {
        if (prevWifiEnabled != isWifiEnabled())
            ((WifiManager)getSystemService(WIFI_SERVICE)).setWifiEnabled(prevWifiEnabled);

        if (prevDataState != getMobileDataState())
            RootUtils.setMobileDataConnection(prevDataState == DataState.ENABLED ? true : false);
    }

    private boolean isWifiEnabled() {
        WifiManager wManager = (WifiManager) getSystemService(WIFI_SERVICE);
        return wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                || wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
    }

    private PowerManager.WakeLock acquireWakelock(int minutes) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MimibazarUpdater::UpdateServiceWakeLock");

        wakeLock.acquire(minutes * 60 * 1000L);
        return wakeLock;
    }

    private boolean tryAssertHasInternet(DataState initialDataState, boolean initialWifiEnabled) {
        // If connection is available in the current state (= without
        // any changes), return true.
        if (isConnectionAvailable())
            return true;

        if (getAllowWifiChange(this)) {
            // If connection is not available and the WIFI is off, turn it on
            // and return true if connection is now available.
            WifiManager wManager = (WifiManager) getSystemService(WIFI_SERVICE);

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
            if (initialDataState == DataState.ENABLED || initialDataState == DataState.UNKNOWN)
                return initialWifiEnabled ? pingConnection() : false;
        }

        if (getAllowDataChange(this)) {
            // If the data were already enabled or we don't have permission to read it (and thus
            // change it), we can't do anything else.
            if (initialDataState == DataState.ENABLED || initialDataState == DataState.UNKNOWN)
                return false;

            boolean set = RootUtils.setMobileDataConnection(true);
            if (set)
                return pingConnection();
        }

        return false;
    }

    private DataState getMobileDataState() {
        try {
            Pair<Boolean, Process> pair = RootUtils.runCommandAsSu("dumpsys telephony.registry | grep mDataConnectionState");
            Process p = pair.second;

            if (!pair.first.booleanValue() || p == null)
                return DataState.UNKNOWN;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null)
                output += (s);

            return output.contains("2") ? DataState.ENABLED : DataState.DISABLED;
        } catch (Exception e) {
            Log.e("UpdateService", Utils.getExceptionAsString(e));
        }

        return DataState.UNKNOWN;
    }

    // Will try to connect for 30 seconds in 3 second intervals.
    private boolean pingConnection() {
        for (byte i = 0; i < 10; ++i) {
            SystemClock.sleep(2950);
            if (isConnectionAvailable())
                return true;
        }

        return false;
    }

    private boolean isConnectionAvailable() {
        try {
            InetAddress ipAddress = InetAddress.getByName("google.com");
            return !"".equals(ipAddress);
        } catch (Exception e) {
            Log.e("UpdateService", Utils.getExceptionAsString(e));
            return false;
        }
    }
}