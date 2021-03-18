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
import java.net.UnknownHostException;

public class UpdateService extends IntentService {
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";
    public enum UpdateResult {
        FULL_SUCCESS, PARTIAL_SUCCESS, //TODO finish this or move it to update class.
    }

    private enum DataState {
        DATA_UNKNOWN, DATA_ENABLED, DATA_DISABLED
    }

    public UpdateService() {
        super("UpdateService");
    }

    public static void startUpdateImmediately(Context context) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(ACTION_UPDATE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);
        PowerManager.WakeLock wakelock = acquireWakelock(12);

        DataState prevMobileDataState = getMobileDataState();
        boolean prevWifiEnabled = isWifiEnabled();

        // Execute only if internet connection could be established.
        if (tryAssertHasInternet(prevMobileDataState, prevWifiEnabled))
            Updater.update(this);
        else
            Notifications.PostDefaultNotification(this, "Nelze aktualizovat Mimibazar",
                    "nelze získat internetové připojení.");

        revertToInitialState(prevMobileDataState, prevWifiEnabled);

        wakelock.release();
    }

    private void revertToInitialState(DataState prevDataState, boolean prevWifiEnabled) {
        if (prevWifiEnabled != isWifiEnabled())
            ((WifiManager)getSystemService(WIFI_SERVICE)).setWifiEnabled(prevWifiEnabled);

        if (prevDataState != getMobileDataState())
            RootUtils.setMobileDataConnection(prevDataState == DataState.DATA_ENABLED ? true : false);
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
        if (initialDataState == DataState.DATA_ENABLED || initialDataState == DataState.DATA_UNKNOWN)
            return initialWifiEnabled ? pingConnection() : false;

        // The data state at this point can only be DISABLED - and if we could get this
        // information, it means that we have root, so we will turn data on and return
        // if connection now works (but we're still checking if it was succesfully set -
        // and if not, we don't check the connection and return false right away.
        boolean set = RootUtils.setMobileDataConnection(true);
        if (set)
            return pingConnection();

        return false;
    }

    private DataState getMobileDataState() {
        try {
            Pair<Boolean, Process> pair = RootUtils.runCommandAsSu("dumpsys telephony.registry | grep mDataConnectionState");
            Process p = pair.second;

            if (!pair.first.booleanValue() || p == null)
                return DataState.DATA_UNKNOWN;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null)
                output += (s);

            return output.contains("2") ? DataState.DATA_ENABLED : DataState.DATA_DISABLED;
        } catch (Exception e) {
            Log.e("UpdateService", Utils.getExceptionAsString(e));
        }

        return DataState.DATA_UNKNOWN;
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