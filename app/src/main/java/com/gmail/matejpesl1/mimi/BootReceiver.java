package com.gmail.matejpesl1.mimi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    // The alarm is unregistered on reboot, so we re-register it on boot (if it should
    // be registered).
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (UpdateServiceAlarmManager.isRegistered(context))
            UpdateServiceAlarmManager.changeRepeatingAlarm(context, true);
    }
}