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
    private static final int internetAssertionPingingDurationSecs = 50;
    public enum DataState { UNKNOWN, ENABLED, DISABLED }

    public static DataState getMobileDataStateRoot() {
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
        int delayMs = (frequencySecs * 1000) - 200; //The check itself takes some time too, so subtract it.
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

    /**Will first try to enable it with WifiManager and then will try to use root.*/
    public static boolean setWifiEnabledRoot(Context context, boolean enabled) {
        RootUtils.runCommandAsSu("input keyevent KEYCODE_WAKEUP");
        return
                ((WifiManager)context.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(enabled)
                || RootUtils.runCommandAsSu("svc wifi " + (enabled ? "enable" : "disable")).first;
    }
    public static boolean setDataEnabledRoot(boolean enabled) {
        RootUtils.runCommandAsSu("input keyevent KEYCODE_WAKEUP");
        return RootUtils.runCommandAsSu("svc data " + (enabled ? "enable" : "disable")).first;
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        return wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED ||
               wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
    }

    /**Will make changes to the WiFi/Data WITHOUT REVERTING IT. */
    public static boolean tryAssertHasInternet(Context context, DataState initialDataState, boolean initialWifiEnabled, boolean allowWifiChange, boolean allowDataChange) {
        // If connection is available in the current state (= without any changes), return true.
        if (isConnectionAvailable())
            return true;

        // WIFI
        if (allowWifiChange && !initialWifiEnabled) {
            // If connection is not available and the WIFI is off, turn it on
            // and return true if connection is now available.
            boolean enableSucceeded = setWifiEnabledRoot(context, true);
            Log.i(TAG, "Wifi enable succeeded: " + enableSucceeded);

            if (enableSucceeded && waitForConnection(internetAssertionPingingDurationSecs, internetAssertionPingingFreqSecs))
                return true;
        }

        // DATA
        if (allowDataChange && initialDataState != DataState.ENABLED) {
            // If we don't have permission to read it (and therefore change it), we can't do anything.
            if (initialDataState == InternetUtils.DataState.UNKNOWN)
                return false;

            // Enable data and return if the connection is available now.
            boolean enableSucceeded = setDataEnabledRoot(true);
            Log.i(TAG, "Data enable succeeded: " + enableSucceeded);

            // If data enable succeeded and connection is available, return true.
            if (enableSucceeded && waitForConnection(internetAssertionPingingDurationSecs, internetAssertionPingingFreqSecs))
                return true;
        }

        Log.i(TAG, "Could not establish internet connection.");
        return false;
    }
}
