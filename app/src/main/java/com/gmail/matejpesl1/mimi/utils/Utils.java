package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;

import com.gmail.matejpesl1.mimi.activities.MainActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class Utils {

    private Utils() {}

    public static void writeToFile(Context context, String filename, String data, int mode) throws IOException {
        OutputStreamWriter outputStreamWriter =
                new OutputStreamWriter(context.openFileOutput(filename, mode));
        outputStreamWriter.write(data);
        outputStreamWriter.close();
    }

    public static void writePref(Context context, String key, String value) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, 0);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(key, value).apply();
    }

    public static String getExceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static boolean hasBatteryException(Context context) {
        String packageName = context.getPackageName();
        PowerManager pm = (PowerManager)context.getSystemService(context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    public static void requestBatteryException(Context context) {
        String packageName = context.getPackageName();
        PowerManager pm = (PowerManager)context.getSystemService(context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("package:" + packageName));
            context.startActivity(intent);
        }
    }

    public static boolean isEmptyOrNull(String str) {
        return str == null || str == "";
    }

    public static String getPref(Context context, String key, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, 0);

        return prefs.getString(key, defaultValue);
    }

    public static String readFromFile(Context context, String filename) throws IOException {
        InputStream inputStream = context.openFileInput(filename);

        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder stringBuilder = new StringBuilder();
            String receiveString = "";

            while ((receiveString = bufferedReader.readLine()) != null)
                stringBuilder.append("\n").append(receiveString);

            inputStream.close();
            return stringBuilder.toString();
        }

        return null;
    }
}
