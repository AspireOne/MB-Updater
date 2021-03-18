package com.gmail.matejpesl1.mimi.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gmail.matejpesl1.mimi.MainActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

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
