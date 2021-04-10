package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;

public class QueuedUpdateWorker extends Worker {
    private final Context context;

    public QueuedUpdateWorker(Context context, WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @Override
    public Result doWork() {
        Log.i("QueuedUpdateWorker", "Update Worker started.");
        //TODO: Reschedule the work
        if (!InternetUtils.isConnectionAvailable()) {
            Log.i("QueuedUpdateWorker", "Could not get internet, returning.");
            return Result.retry();
        }

        Log.i("QueuedUpdateWorker", "Update Worker requirements/constraints passed, starting update.");
        UpdateService.startUpdateImmediately(context);
        Log.i("QueuedUpdateWorker", "Update Worker finished, returning.");
        return Result.success();
    }
}