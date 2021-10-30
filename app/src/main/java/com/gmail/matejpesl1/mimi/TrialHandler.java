package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;

import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.concurrent.TimeUnit;

public class TrialHandler {
    private static final String TAG = QueuedUpdateWorker.class.getSimpleName();
    private static final String PREF_REGISTER_TIME_SECS = "register_time_secs";

    public void registerTrial(Context context) {
        if (Utils.getLongPref(context, PREF_REGISTER_TIME_SECS, -1) != -1) {
            Log.i(TAG, "Phone already registered.");
            return;
        }

        Utils.writePref(context, PREF_REGISTER_TIME_SECS, System.currentTimeMillis()/1000);
        Log.i(TAG, "Phone registered.");
    }

    public int tryGetPassedDays(Context context) {
        // Using 0 instead of -1 so that when we divide by 1000 it's still 0 and it's clear that
        // the default value was returned, and therefore that the device isn't registered.
        // Note that the unix timestamp divided by 1000 DOES FIT INTO AN INT.
        long registerTimeSecs = Utils.getLongPref(context, PREF_REGISTER_TIME_SECS, 0)/1000;
        if (registerTimeSecs == 0)
            return -1;

        long currTimeSecs = System.currentTimeMillis()/1000;
        return (int)TimeUnit.SECONDS.toDays(currTimeSecs);
    }
}
