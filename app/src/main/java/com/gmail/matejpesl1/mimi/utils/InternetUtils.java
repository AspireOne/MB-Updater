package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.util.Pair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class InternetUtils {
    private static final String TAG = InternetUtils.class.getSimpleName();
    private static final int internetAssertionPingingFreqSecs = 5;
    private static final int internetAssertionPingingDurationSecs = 60;
    public enum DataState { UNKNOWN, ENABLED, DISABLED }

    public static DataState getMobileDataState() {
        try {
            Pair<Boolean, Process> pair = RootUtils.runCommandAsSu("dumpsys telephony.registry | grep mDataConnectionState");
            Process p = pair.second;

            if (!pair.first || p == null)
                return DataState.UNKNOWN;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String output = "";
            String s;
            // It will have about 2 lines, so no need to use StringBuilder.
            while ((s = stdInput.readLine()) != null)
                output += (s);

            return output.contains("2") ? DataState.ENABLED : DataState.DISABLED;
        } catch (Exception e) {
            Log.e(TAG, Utils.getExAsStr(e));
        }

        return DataState.UNKNOWN;
    }

    public static boolean waitForConnection(int durationSecs, int frequencySecs) {
        int iterations = durationSecs/frequencySecs;
        int delayMs = (frequencySecs * 1000) - 100; //The check itself takes some time too, so subtract it.
        for (byte i = 0; i < iterations; ++i) {
            SystemClock.sleep(delayMs);
            if (isConnectionAvailable())
                return true;
        }

        return false;
    }

    public static boolean isConnectionAvailable() {
        try {
            InetAddress ipAddress = InetAddress.getByName("google.com");
            boolean resolved = !ipAddress.toString().equals("");
            Log.i(TAG, "Connection check succeeded: " + resolved);
            return resolved;
        } catch (Exception e) {
            Log.i(TAG, "Connection check failed");
            return false;
        }
    }

    public static WifiManager.WifiLock acquireWifiLock(Context context, String tag) {
        WifiManager.WifiLock lock =
                ((WifiManager)context.getSystemService(Context.WIFI_SERVICE)).createWifiLock(tag);
        lock.acquire();
        return lock;
    }
    public static void setWifiEnabled(Context context, boolean enabled) {
        ((WifiManager)context.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(enabled);
    }
    public static void setDataEnabled(boolean enabled) {
        RootUtils.setMobileDataConnection(enabled);
    }

    public static void revertToInitialState(Context context, DataState prevDataState, boolean prevWifiEnabled) {
        if (prevWifiEnabled != isWifiEnabled(context))
            setWifiEnabled(context, prevWifiEnabled);

        if (prevDataState != getMobileDataState())
            setDataEnabled(prevDataState == DataState.ENABLED);
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                || wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
    }

    public static boolean tryAssertHasInternet(Context context, DataState initialDataState, boolean initialWifiEnabled, boolean allowWifiChange, boolean allowDataChange) {
        // If connection is available in the current state (= without any changes), return true.
        if (isConnectionAvailable())
            return true;

        if (allowWifiChange) {
            // If connection is not available and the WIFI is off, turn it on
            // and return true if connection is now available.
            WifiManager wManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

            // A workaround for wifi not connecting on some systems unless the screen is turned on.
            RootUtils.runCommandAsSu("input keyevent KEYCODE_WAKEUP");

            if (!initialWifiEnabled) {
                boolean enableSucceeded = wManager.setWifiEnabled(true);
                Log.i(TAG, "Wifi enable succeeded: " + enableSucceeded);

                if (enableSucceeded && waitForConnection(internetAssertionPingingDurationSecs, internetAssertionPingingFreqSecs))
                    return true;
            }

            // If connection is not available even when the WIFI is on, turn it off
            // (so that it doesn't interfere with mobile data).
            boolean disableSucceeded = wManager.setWifiEnabled(false);
            Log.i(TAG, "Wifi disable succeeded: " + disableSucceeded);

            // If data were already enabled (or unknown) and wifi too, check if the
            // data will work now without the wifi interfering and return it.
            // If data were already enabled (or unknown) but wifi not, return false - we can't
            // do anything else.
            if (initialDataState == InternetUtils.DataState.ENABLED || initialDataState == InternetUtils.DataState.UNKNOWN)
                return (initialWifiEnabled && disableSucceeded) && waitForConnection(internetAssertionPingingDurationSecs, internetAssertionPingingFreqSecs);
        }

        if (allowDataChange) {
            // If the data were already enabled or we don't have permission to read it (and thus
            // change it), we can't do anything else.
            if (initialDataState == InternetUtils.DataState.ENABLED || initialDataState == InternetUtils.DataState.UNKNOWN)
                return false;

            // Enable data as a last try and return if the connection is available now.
            boolean setSucceeded = RootUtils.setMobileDataConnection(true);
            Log.i(TAG, "Data change allowed. Change succeeded: " + setSucceeded);
            if (setSucceeded)
                return waitForConnection(internetAssertionPingingDurationSecs, internetAssertionPingingFreqSecs);
        }

        Log.i(TAG, "Could not establish internet connection.");
        return false;
    }
}
