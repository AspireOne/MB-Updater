package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.gmail.matejpesl1.mimi.services.UpdateService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class InternetUtils {
    private static final String TAG = "InternetUtils";
    public enum DataState { UNKNOWN, ENABLED, DISABLED }

    public static DataState getMobileDataState() {
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
            Log.e(TAG, Utils.getExceptionAsString(e));
        }

        return DataState.UNKNOWN;
    }

    // Will try to connect for 30 seconds in 3 second intervals.
    public static boolean pingConnection() {
        for (byte i = 0; i < 10; ++i) {
            SystemClock.sleep(2950);
            if (isConnectionAvailable())
                return true;
        }

        return false;
    }

    public static boolean isConnectionAvailable() {
        try {
            InetAddress ipAddress = InetAddress.getByName("google.com");
            return !"".equals(ipAddress);
        } catch (Exception e) {
            Log.e(TAG, Utils.getExceptionAsString(e));
            return false;
        }
    }


    public static void revertToInitialState(Context context, DataState prevDataState, boolean prevWifiEnabled) {
        if (prevWifiEnabled != isWifiEnabled(context))
            ((WifiManager)context.getSystemService(context.WIFI_SERVICE)).setWifiEnabled(prevWifiEnabled);

        if (prevDataState != getMobileDataState())
            RootUtils.setMobileDataConnection(prevDataState == DataState.ENABLED ? true : false);
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        return wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                || wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
    }
}
