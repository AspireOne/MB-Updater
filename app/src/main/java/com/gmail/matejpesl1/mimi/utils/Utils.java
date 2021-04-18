package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.PowerManager;

import com.gmail.matejpesl1.mimi.activities.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public static void saveFile(Context context, String filename, byte[] data) throws IOException {
        File file = new File(context.getFilesDir(), filename);

        if (file.exists())
            file.delete();

        file.createNewFile();

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    public static String getExceptionAsString(Exception e) {
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

    public static String dateToDigitalTime(Date time) {
        return new SimpleDateFormat("H:mm", new Locale("cs", "CZ")).format(time);
    }

    public static boolean isEmptyOrNull(String str) {
        return str == null || str.equals("");
    }

    public static void writePref(Context context, String key, float value) {
        getPrefsEditor(context).putFloat(key, value).apply();
    }
    public static void writePref(Context context, String key, long value) {
        getPrefsEditor(context).putLong(key, value).apply();
    }
    public static void writePref(Context context, String key, boolean value) {
        getPrefsEditor(context).putBoolean(key, value).apply();
    }
    public static void writePref(Context context, String key, int value) {
        getPrefsEditor(context).putInt(key, value).apply();
    }
    public static void writePref(Context context, String key, String value) {
        getPrefsEditor(context).putString(key, value).apply();
    }

    private static SharedPreferences.Editor getPrefsEditor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.GLOBAL_PREFS_NAME, 0);
        return prefs.edit();
    }

    public static boolean getBooleanPref(Context context, int keyId, boolean defaultValue) {
        return getBooleanPref(context, context.getString(keyId), defaultValue);
    }
    public static boolean getBooleanPref(Context context, String key, boolean defaultValue) {
        return getPrefs(context).getBoolean(key, defaultValue);
    }

    public static int getIntPref(Context context, int keyId, int defaultValue) {
        return getIntPref(context, context.getString(keyId), defaultValue);
    }
    public static int getIntPref(Context context, String key, int defaultValue) {
        return getPrefs(context).getInt(key, defaultValue);
    }

    public static long getLongPref(Context context, int keyId, long defaultValue) {
        return getLongPref(context, context.getString(keyId), defaultValue);
    }
    public static long getLongPref(Context context, String key, long defaultValue) {
        return getPrefs(context).getLong(key, defaultValue);
    }

    public static String getStringPref(Context context, int keyId, String defaultValue) {
        return getStringPref(context, context.getString(keyId), defaultValue);
    }
    public static String getStringPref(Context context, String key, String defaultValue) {
        return getPrefs(context).getString(key, defaultValue);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(MainActivity.GLOBAL_PREFS_NAME, 0);
    }
}
