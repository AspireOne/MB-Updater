package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.PowerManager;

import com.gmail.matejpesl1.mimi.activities.MainActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Locale;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class Utils {
    
    private Utils() {}

    public static String dateToCzech(Date time) {
        return new SimpleDateFormat("dd. MM. yyyy H:mm (EEEE)", new Locale("cs", "CZ")).format(time);
    }

    public static String dateToDigitalTime(Date time) { return new SimpleDateFormat("H:mm", new Locale("cs", "CZ")).format(time); }

    public static String getExAsStr(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static boolean hasBatteryException(Context context) {
        String packageName = context.getPackageName();
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    public static void requestBatteryException(Context context) {
        String packageName = context.getPackageName();
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("package:" + packageName));
            context.startActivity(intent);
        }
    }

    public static boolean isEmptyOrNull(String str) { return str == null || str.equals(""); }

    public static void writePref(Context context, String key, float value) { writePref(context, key, value+""); }
    public static void writePref(Context context, String key, long value) { writePref(context, key, value+""); }
    public static void writePref(Context context, String key, boolean value) { writePref(context, key, value+""); }
    public static void writePref(Context context, String key, int value) { writePref(context, key, value+""); }
    public static void writePref(Context context, String key, String value) {
        context.getSharedPreferences(MainActivity.PREFS_NAME, 0).edit().putString(key, value).apply();
    }

    public static boolean getBooleanPref(Context context, int keyId, boolean defaultValue) { return getBooleanPref(context, context.getString(keyId), defaultValue); }
    // A workaround for settingsPreferences saving boolean as boolean instead of string.
    public static boolean getBooleanPref(Context context, String key, boolean defaultValue){
        String pref;
        try {
            pref = getPref(context, key, defaultValue+"");
        } catch (ClassCastException e) {
            return context.getSharedPreferences(MainActivity.PREFS_NAME, 0).getBoolean(key, defaultValue);
        }

        return Boolean.parseBoolean(pref);
    }

    public static long getLongPref(Context context, int keyId, long defaultValue) { return Long.parseLong(getPref(context, keyId, defaultValue+"")); }
    public static long getLongPref(Context context, String key, long defaultValue){ return Long.parseLong(getPref(context, key, defaultValue+"")); }

    public static int getIntPref(Context context, int keyId, int defaultValue) { return Integer.parseInt(getPref(context, keyId, defaultValue+"")); }
    public static int getIntPref(Context context, String key, int defaultValue){ return Integer.parseInt(getPref(context, key, defaultValue+"")); }

    public static String getPref(Context context, int keyId, String defaultValue) { return getPref(context, context.getString(keyId), defaultValue); }
    public static String getPref(Context context, String key, String defaultValue){
        return context.getSharedPreferences(MainActivity.PREFS_NAME, 0).getString(key, defaultValue);
    }
}
