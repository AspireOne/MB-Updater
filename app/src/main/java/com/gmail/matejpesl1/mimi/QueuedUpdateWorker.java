package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;

public class QueuedUpdateWorker extends Worker {
    private static final String TAG = "QueuedUpdateWorker";
    private final Context context;

    public QueuedUpdateWorker(Context context, WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Update Worker started.");
        if (!InternetUtils.isConnectionAvailable()) {
            Log.i(TAG, "Could not get internet, returning.");
            return Result.retry();
        }

        Log.i(TAG, "Update Worker requirements/constraints passed, starting update.");
        UpdateService.startUpdateImmediately(context);
        Log.i(TAG, "Update Worker finished, returning.");
        return Result.success();
    }
}