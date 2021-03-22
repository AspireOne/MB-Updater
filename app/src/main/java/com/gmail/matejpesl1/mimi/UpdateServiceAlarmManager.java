package com.gmail.matejpesl1.mimi;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.icu.util.Calendar;
import android.content.Intent;
import android.content.SharedPreferences;
import android.renderscript.Sampler;
import android.util.Log;

import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.Utils;

public class UpdateServiceAlarmManager {
    private static final String PREF_ACTIVE = "Alarm Scheduled";
    private static final String PREF_UPDATE_MINUTE = "Update Minutes";
    private static final String PREF_UPDATE_HOUR = "Update Hour";
    private static final String PREF_UPDATE_DAY_PART = "Update Day Part";

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

        Utils.writePref(context, PREF_ACTIVE, Boolean.toString(register));
    }

    public static boolean isRegistered(Context context) {
        return Utils.getPref(context, PREF_ACTIVE, "false").equals("true");
    }

    public static void changeUpdateTime(Context context, int minute, int hour) {
        int finalHour = hour;
        int finalDayPart = Calendar.AM;

        if (hour == 12) {
            finalHour = 0;
            finalDayPart = Calendar.PM;
        } else if (hour == 0) {
            finalDayPart = Calendar.AM;
        } else if (hour > 12)
            finalHour -= 12;

        Utils.writePref(context, PREF_UPDATE_MINUTE, minute+"");
        Utils.writePref(context, PREF_UPDATE_HOUR, finalHour+"");
        Utils.writePref(context, PREF_UPDATE_DAY_PART, finalDayPart+"");

        // If the alarm is registered, re-register it to the new time.
        if (isRegistered(context)) {
            changeRepeatingAlarm(context, true);
        }
    }

    public static Calendar getCurrUpdateCalendar(Context context) {
        String hourStr = Utils.getPref(context, PREF_UPDATE_HOUR, "");
        String minuteStr = Utils.getPref(context, PREF_UPDATE_MINUTE, "");
        String dayPartStr = Utils.getPref(context, PREF_UPDATE_DAY_PART, "");

        if (hourStr.equals(""))
            Utils.writePref(context, PREF_UPDATE_HOUR, (hourStr = "7"));
        if (minuteStr.equals(""))
            Utils.writePref(context, PREF_UPDATE_MINUTE, (minuteStr = "45"));
        if (dayPartStr.equals(""))
            Utils.writePref(context, PREF_UPDATE_DAY_PART, (dayPartStr = Calendar.AM+""));

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, Integer.parseInt(minuteStr));
        calendar.set(Calendar.HOUR, Integer.parseInt(hourStr));
        calendar.set(Calendar.AM_PM, Integer.parseInt(dayPartStr));

        if (calendar.getTimeInMillis() <= System.currentTimeMillis())
            calendar.set(Calendar.DAY_OF_WEEK, calendar.get(Calendar.DAY_OF_WEEK) + 1);

        return calendar;
    }
}
