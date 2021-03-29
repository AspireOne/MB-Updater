package com.gmail.matejpesl1.mimi;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.gmail.matejpesl1.mimi.utils.Utils.getExceptionAsString;

public class Requester {
    private static final String TAG = "REQUESTER";
    // HTTP
    public enum RequestMethod {POST, GET}
    public int requestThrottleMs;
    private long lastRequest = 0;
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public Requester(int requestThrottleMs) {
        this.requestThrottleMs = requestThrottleMs;
    }

    public static @Nullable String getBodyOrNull(Pair<Boolean, Response> result) {
        if (!result.first.booleanValue())
            return null;

        try {
            String body = result.second.body().string();
            result.second.close();
            return body;
        } catch (Exception e) {
            Log.e(TAG, getExceptionAsString(e));
            return null;
        }
    }

    public @Nullable String getWebsiteBodyOrNull(String url, RequestMethod method, @Nullable RequestBody body) {
        return getBodyOrNull(tryMakeRequest(url, method, body));
    }

    public Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method, @Nullable RequestBody body) {
        if (requestThrottleMs > 0)
            throttle();

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (method == RequestMethod.POST) {
            requestBuilder.post(body);
            requestBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded");
        }
        else
            requestBuilder.get();

        Call call = HTTP_CLIENT.newCall(requestBuilder.build());
        Response response = null;
        try {
            response = call.execute();
        } catch (Exception e) {
            Log.e(TAG, getExceptionAsString(e));
        }

        return new Pair(response != null && response.isSuccessful(), response);
    }

    private void throttle() {
        while ((System.currentTimeMillis() - lastRequest) < requestThrottleMs) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, getExceptionAsString(e));
            }
        }

        lastRequest = System.currentTimeMillis();
    }
}
