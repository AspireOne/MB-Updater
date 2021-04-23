package com.gmail.matejpesl1.mimi.utils;

import android.util.Log;
import androidx.core.util.Pair;

import java.io.DataOutputStream;
import java.io.IOException;

public class RootUtils {
    private static final String TAG = "RootUtils";
	private RootUtils() {}

    public static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("ls /data\n");
            os.writeBytes("exit\n");
            os.flush();
            try {
                p.waitFor();
                return p.exitValue() == 0;
            } catch (InterruptedException e) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static Process askForRoot() {
        try {
            return Runtime.getRuntime().exec("su");
        } catch (Exception e) {
            Log.e(TAG, Utils.getExAsStr(e));
            return null;
        }
    }

    public static Pair<Boolean, Process> runCommandAsSu(String command) {
        Process p = null;
        boolean success;

        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(p.getOutputStream());

            outputStream.writeBytes(command + (command.endsWith("\n ") ? "" : command.endsWith("\n") ? " " : "\n "));
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                p.waitFor();
                success = p.exitValue() == 0;
            } catch (InterruptedException e) {
                Log.e(TAG, Utils.getExAsStr(e));
                success = false;
            }

            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, Utils.getExAsStr(e));
            success = false;
        }

        return new Pair<>(success, p);
    }

    public static boolean setMobileDataConnection(boolean enabled) {
        return runCommandAsSu("svc data " + (enabled ? "enable" : "disable")).first;
    }
}
