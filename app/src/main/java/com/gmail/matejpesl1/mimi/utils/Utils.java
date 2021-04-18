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

    public static void writePref(Context context, String key, boolean value) { writePref(context, key, Boolean.toString(value)); }
    public static void writePref(Context context, String key, int value) { writePref(context, key, value+""); }
    public static void writePref(Context context, String key, String value) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.GLOBAL_PREFS_NAME, 0);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(key, value).apply();
    }

    public static boolean getBooleanPref(Context context, int key, boolean defaultValue) {
        return getBooleanPref(context, context.getString(key), defaultValue);
    }
    public static boolean getBooleanPref(Context context, String key, boolean defaultValue) {
        String pref = getPref(context, key, null);
        return pref == null ? defaultValue : Boolean.parseBoolean(pref);
    }

    public static int getNumberPref(Context context, int key, int defaultValue) {
        return getNumberPref(context, context.getString(key), defaultValue);
    }
    public static int getNumberPref(Context context, String key, int defaultValue) {
        String pref = getPref(context, key, null);
        return pref == null ? defaultValue : Integer.parseInt(pref);
    }

    public static String getPref(Context context, int keyId, String defaultValue) {
        return getPref(context, context.getString(keyId), defaultValue);
    }
    public static String getPref(Context context, String key, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.GLOBAL_PREFS_NAME, 0);
        return prefs.getString(key, defaultValue);
    }
}
