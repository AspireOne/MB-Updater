package com.gmail.matejpesl1.mimi.utils;

import android.util.Pair;

import java.io.DataOutputStream;
import java.io.IOException;

public class RootUtils {
    private static final String ENABLE_DATA_COMMAND = "svc data enable";
    private static final String DISABLE_DATA_COMMAND = "svc data disable";

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

    public static void askForRoot() {
        try {
            Runtime.getRuntime().exec("su");
        } catch (Exception e) {
            e.printStackTrace();
            // Ignored
        }
    }

    public static Pair<Boolean, Process> runCommandAsSu(String command) {
        Process su = null;
        boolean success = true;

        try {
            su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            outputStream.writeBytes(command + (command.endsWith("\n ") ? "" : command.endsWith("\n") ? " " : "\n "));
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
                success = false;
            }

            outputStream.close();
        } catch(IOException e) {
            e.printStackTrace();
            success = false;
        }

        return new Pair(new Boolean(success && su != null), su);
    }

    public static boolean setMobileDataConnection(boolean enabled) {
        return runCommandAsSu("svc data " + (enabled ? "enable" : "disable")).first.booleanValue();
    }
}
