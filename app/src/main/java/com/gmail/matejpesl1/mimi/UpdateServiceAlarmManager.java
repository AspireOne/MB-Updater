package com.gmail.matejpesl1.mimi;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.icu.util.Calendar;
import android.content.Intent;
import android.util.Log;

import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.Utils;

public class UpdateServiceAlarmManager {
    private static final String TAG = "UpdateServiceAlarmManager";
    private static final String PREF_ACTIVE = "alarm_scheduled";
    private static final String PREF_UPDATE_MINUTE = "update_minute";
    private static final String PREF_UPDATE_HOUR = "update_hour";
    private static final String PREF_UPDATE_DAY_PART = "update_day_part";

    public static void changeRepeatingAlarm(Context context, boolean register) {
        AlarmManager manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, UpdateService.class);
        intent.setAction(UpdateService.ACTION_UPDATE);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        manager.cancel(pendingIntent);

        if (register)
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    getCurrUpdateCalendar(context).getTimeInMillis(),
                    pendingIntent);

        Utils.writePref(context, PREF_ACTIVE, register);
    }

    public static boolean isRegistered(Context context) {
        return Utils.getBooleanPref(context, PREF_ACTIVE, false);
    }

    public static void changeUpdateTime(Context context, int minute, int hour) {
        int finalHour;
        int finalDayPart;

        if (hour > 12) {
            finalHour = hour - 12;
            finalDayPart = Calendar.PM;
        } else if (hour == 12) {
            finalHour = 0;
            finalDayPart = Calendar.PM;
        }
        else {
            finalHour = hour;
            finalDayPart = Calendar.AM;
        }

        Utils.writePref(context, PREF_UPDATE_MINUTE, minute);
        Utils.writePref(context, PREF_UPDATE_HOUR, finalHour);
        Utils.writePref(context, PREF_UPDATE_DAY_PART, finalDayPart);

        // If the alarm is registered, re-register it to the new time.
        if (isRegistered(context))
            changeRepeatingAlarm(context, true);
    }

    public static Calendar getCurrUpdateCalendar(Context context) {
        int hour = Utils.getIntPref(context, PREF_UPDATE_HOUR, -1);
        int minute = Utils.getIntPref(context, PREF_UPDATE_MINUTE, -1);
        int dayPart = Utils.getIntPref(context, PREF_UPDATE_DAY_PART, -1);

        if (hour == -1)
            Utils.writePref(context, PREF_UPDATE_HOUR, (hour = 7));
        if (minute == -1)
            Utils.writePref(context, PREF_UPDATE_MINUTE, (minute = 45));
        if (dayPart == -1)
            Utils.writePref(context, PREF_UPDATE_DAY_PART, (dayPart = Calendar.AM));

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.HOUR, hour);
        calendar.set(Calendar.AM_PM, dayPart);

        // If the time is in the past (e.g. time: 6:00 | curr: 7:00) -> set it for the next day.
        if (calendar.getTimeInMillis() <= System.currentTimeMillis())
            calendar.add(Calendar.DAY_OF_WEEK, 1);

        Log.i(TAG, "Time set to: " + Utils.dateToCzech(calendar.getTime()));
        return calendar;
    }
}
