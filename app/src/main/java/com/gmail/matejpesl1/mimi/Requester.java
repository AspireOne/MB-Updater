package com.gmail.matejpesl1.mimi;

import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Requester {
    private static final String TAG = Requester.class.getSimpleName();
    private final List<Cookie> cookies = new ArrayList<>();
    private final OkHttpClient httpClient;
    private final OkHttpClient noCookiesClient;
    private long lastRequestUnix = 0;

    public enum RequestMethod {POST, GET}
    public int requestThrottleMs = 0;

    public Requester(int requestThrottleMs, Context context) {
        this.requestThrottleMs = requestThrottleMs;
        File cacheDir = new File(context.getCacheDir(), "network");
        if (!cacheDir.exists())
            cacheDir.mkdir();

        Cache cache = new Cache(cacheDir, 10 * 1024 * 1024);

        final ClearableCookieJar cookieJar = new PersistentCookieJar(new SetCookieCache(), new CookiePersistor() {
            @Override
            public List<Cookie> loadAll() { return cookies; }
            @Override
            public void saveAll(Collection<Cookie> cookies) { cookies.addAll(cookies); }
            @Override
            public void removeAll(Collection<Cookie> cookies) { cookies.removeAll(cookies); }
            @Override
            public void clear() { cookies.clear(); }
        });
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .cookieJar(cookieJar)
                .followRedirects(true)
                .cache(cache)
                .build();

        noCookiesClient = httpClient.newBuilder()
                .cookieJar(null)
                .cache(null)
                .build();
    }

    public static @Nullable String getBodyOrNull(Pair<Boolean, Response> result) {
        if (!result.first)
            return null;

        try {
            String body = result.second.body().string();
            result.second.close();
            return body;
        } catch (Exception e) {
            Log.e(TAG, getExAsStr(e));
            return null;
        }
    }

    public @Nullable String getWebsiteBodyOrNull(String url, RequestMethod method) {
        return getWebsiteBodyOrNull(url, method, null, null, null);
    }
    public @Nullable String getWebsiteBodyOrNull(String url, RequestMethod method, @Nullable RequestBody body) {
        return getWebsiteBodyOrNull(url, method, body, null, null);
    }
    public @Nullable String getWebsiteBodyOrNull(String url, RequestMethod method, @Nullable RequestBody body, @Nullable Pair<String, String> headers[]) {
        return getBodyOrNull(tryMakeRequest(url, method, body, headers, null));
    }
    public @Nullable String getWebsiteBodyOrNull(String url, RequestMethod method, @Nullable RequestBody body, @Nullable Pair<String, String> headers[], @Nullable Boolean cookied) {
        return getBodyOrNull(tryMakeRequest(url, method, body, headers, cookied));
    }

    public Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method) {
        return tryMakeRequest(url, method, null, null, null);
    }
    public Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method, @Nullable RequestBody body) {
        return tryMakeRequest(url, method, body, null, null);
    }
    public Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method, @Nullable RequestBody body, @Nullable Pair<String, String> headers[]) {
        return tryMakeRequest(url, method, body, headers, null);
    }
    public Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method, @Nullable RequestBody body, @Nullable Pair<String, String> headers[], @Nullable Boolean cookied) {
        if (requestThrottleMs > 0)
            throttle();

        if (cookied = null)
            cookied = true;

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null)
            for (int i = 0; i < headers.length; i++)
                requestBuilder.addHeader(headers[i].first, headers[i].second);

        if (method == RequestMethod.POST) {
            requestBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded");
            requestBuilder.post(body);
        }
        else
            requestBuilder.get();

        Call call = (cookied ? httpClient : noCookiesClient).newCall(requestBuilder.build());
        Response response = null;
        try {
            response = call.execute();
        } catch (Exception e) {
            Log.e(TAG, getExAsStr(e));
        }

        return new Pair<>(response != null && response.isSuccessful(), response);
    }

    private void throttle() {
        while ((System.currentTimeMillis() - lastRequestUnix) < requestThrottleMs)
            Utils.sleep(TAG, 50);

        lastRequestUnix = System.currentTimeMillis();
    }
}
