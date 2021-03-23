package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.core.content.FileProvider;

import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Scanner;

public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String PREF_LAST_DOWNLOADED_APK_VERSION = "Last Downloaded APK version";
    private static final String LATEST_RELEASE_JSON_LINK = "https://api.github.com/repos/AspireOne/hub/releases/latest";
    private static final String APK_DOWNLOAD_LINK = "https://github.com/AspireOne/hub/releases/latest/download/updater.apk";
    private static final String APK_NAME = "updater.apk";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final long VERSION_CACHE_TIME = 600000; // 10 minutes.

    private static long lastVersionCheck = 0;
    private static int cachedVersion = 0;
    private static boolean downloading = false;

    public static boolean isUpdateAvailable() {
        return getNewestVerNum() > BuildConfig.VERSION_CODE;
    }

    public static void requestInstall(Context context) {
        while (downloading) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, Utils.getExceptionAsString(e));
            }
        }
        
        if (!isDownloadedApkLatest(context))
            downloadApk(context);

        Uri uri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".provider",
                getApk(context));

        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .setDataAndType(uri, APK_MIME_TYPE)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(intent);
    }

    public static void installDirectlyWithRoot(Context context) {
        while (downloading) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, Utils.getExceptionAsString(e));
            }
        }

        if (!isDownloadedApkLatest(context))
            downloadApk(context);

        File file = getApk(context);

        Log.e(TAG, String.format("cat %s | pm install -S %s", file.getAbsolutePath(), file.length()));
        Pair<Boolean, Process> result = RootUtils.runCommandAsSu(
                String.format("cat %s | pm install -S %s", file.getAbsolutePath(), file.length()));

        Log.e(TAG, "direct installation success: " + result.first.booleanValue());
    }

    private static int getNewestVerNum() {
        if ((System.currentTimeMillis() - lastVersionCheck) < VERSION_CACHE_TIME)
            return cachedVersion;

        lastVersionCheck = System.currentTimeMillis();

        try {
            URL url = new URL(LATEST_RELEASE_JSON_LINK);
            Scanner sc = new Scanner(url.openStream(), "UTF-8");
            String releaseInfo = sc.useDelimiter("\\A").next();
            sc.close();
            int numBeginChar = releaseInfo.indexOf("\"v") + 2;
            int numEndChar = releaseInfo.indexOf("\"", numBeginChar);
            String tag = releaseInfo.substring(numBeginChar, numEndChar).replace(".", "");
            return (cachedVersion = Integer.parseInt(tag));
        } catch (Exception e) {
            Log.e(TAG, Utils.getExceptionAsString(e));
            return -1;
        }
    }

    private static File getApk(Context context) {
        File externalDir = context.getExternalFilesDir(null);
        return new File(externalDir, APK_NAME);
    }

    public static boolean isDownloadedApkLatest(Context context) {
        String pref = Utils.getPref(context, PREF_LAST_DOWNLOADED_APK_VERSION, "0");
        String latest = getNewestVerNum()+"";

        return pref.equals(latest) && getApk(context).exists();
    }

    public static boolean downloadApk(Context context) {
        if (downloading || isDownloadedApkLatest(context))
            return true;

        downloading = true;

        FileOutputStream fos = null;
        FileChannel fch = null;
        try {
            ReadableByteChannel readableByteChannel =
                    Channels.newChannel(new URL(APK_DOWNLOAD_LINK).openStream());

            fos = new FileOutputStream(getApk(context));
            fch = fos.getChannel();

            fch.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            Utils.writePref(context, PREF_LAST_DOWNLOADED_APK_VERSION, getNewestVerNum()+"");
            return true;
        } catch (Exception e) {
            Log.e(TAG, Utils.getExceptionAsString(e));
        } finally {
            downloading = false;
            try {
                if (fos != null)
                    fos.close();

                if (fch != null)
                    fch.close();
            } catch (Exception e) {
                Log.e(TAG, Utils.getExceptionAsString(e));
            }
        }

        return false;
    }
}
