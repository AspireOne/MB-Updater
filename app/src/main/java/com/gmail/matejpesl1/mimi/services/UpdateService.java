package com.gmail.matejpesl1.mimi.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.utils.RootUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UpdateService extends IntentService {
    public static final String ACTION_UPDATE = "com.gmail.matejpesl1.mimi.action.UPDATE";
    PowerManager.WakeLock wakeLock;

    public UpdateService() {
        super("UpdateService");
    }

    public static void startActionUpdate(Context context) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(ACTION_UPDATE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MimibazarUpdater::UpdateServiceWakelock");

        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        WifiManager wManager = (WifiManager) getSystemService(WIFI_SERVICE);
        boolean prevMobileDataEnabled = isMobileDataEnabled();
        boolean prevWifiEnabled = wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED || wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;

        boolean hasInternet = tryAssertHasInternet();

        execute();

        boolean mobileDataEnabled = isMobileDataEnabled();
        boolean wifiEnabled = wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED || wManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;

        if (!prevMobileDataEnabled && mobileDataEnabled)
            RootUtils.setMobileDataConnection(false);

        wManager.setWifiEnabled(prevWifiEnabled);
        wakeLock.release();
    }

    private void execute() {

    }

    private boolean tryAssertHasInternet() {
        if (isConnectionAvailable())
            return true;

        WifiManager wManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int prevWifiState = wManager.getWifiState();

        wManager.setWifiEnabled(true);

        if (prevWifiState == WifiManager.WIFI_STATE_DISABLED || prevWifiState == WifiManager.WIFI_STATE_DISABLING)
        wManager.setWifiEnabled(false);

        if (!RootUtils.isRootAvailable())
            return false;

        RootUtils.setMobileDataConnection(true);

        return pingConnection();
    }

    private boolean isMobileDataEnabled() {
        try {
            Process proc = RootUtils.runCommandAsSu("dumpsys telephony.registry | grep mDataConnectionState").second;
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null)
                output += (s);

            return output.contains("2");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean pingConnection() {
        for (int i = 0; i < 10; ++i) {
            SystemClock.sleep(3000);
            if (isConnectionAvailable())
                return true;
        }

        return false;
    }

    private boolean isConnectionAvailable() {
        try {
            InetAddress ipAddress = InetAddress.getByName("google.com");
            return !"".equals(ipAddress);
        } catch (UnknownHostException | SecurityException e) {
            return false;
        }
    }
}