package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.gmail.matejpesl1.mimi.utils.Utils.*;

public class Updater {
    // Prefs
    private static final String PREF_IDS = "IDs Of Items";
    private static final String PREF_AMOUNT_OF_PAGES = "Amount Of Pages To Update";
    private static final String PREF_CURR_ID_INDEX = "Index Of Current ID";

    // Patterns
    private static final Pattern ID_REGEX_PATTERN = Pattern.compile("(?<=href=\"https:\\/\\/www\\.mimibazar\\.cz\\/inzerat\\/)\\d+(?=\\/.*\")");
    private static final Pattern UPDATES_AMOUNT_REGEX_PATTERN = Pattern.compile("(?<=Stále lze využít ).*(?= aktualiza)");
    private static final Pattern UPDATES_ONE_REGEX_PATTERN = Pattern.compile("(?<=Stále lze využít )jednu(?= aktualiza)");
    private static final Pattern UPDATES_NONE_REGEX_PATTERN = Pattern.compile("Dnes jste využili všechny aktualizace");

    // HTTP
    private enum RequestMethod {POST, GET}
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private static final RequestBody REQUEST_BODY = new FormBody.Builder()
            .add("login", "alexandra11")
            .add("password", "quksilver")
            .add("log_in", "ok")
            .build();

    // Other
    private final static int REQUEST_THROTTLE_MS = 400;
    private static long lastRequest = 0;


    public void changeAmountOfUpdatedPages(Context context, int amount) {
        writePref(context, PREF_AMOUNT_OF_PAGES, amount+"");
    }

    public boolean tryForceRecreateIdList(Context context) {
        boolean recreated = tryRecreatePrefIds(context);
        if (!recreated)
            return false;

        writePref(context, PREF_CURR_ID_INDEX, "0");
        return true;
    }

    public static void update(Context context) {
        if (!makeChecksAndNotifyAboutErrors(context))
            return;

        if (tryGetRemainingUpdates() == 0) {
            Log.d("", "Mimibazar was attempted to be updated for the 2nd time, but it's already" +
                    "updated");
            return;
        }

        String error = execute(context);

        if (error != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli runtime chybě",
                    error);
        } else {
            Notifications.PostDefaultNotification(context, "Mimibazar úspěšně aktualizován", "");
        }
    }

    private static boolean makeChecksAndNotifyAboutErrors(Context context) {
        String externalError = checkExternalErrors();

        if (externalError != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli externí chybě",
                    externalError);
            return false;
        }

        String internalError = checkInternalErrors(context);
        if (internalError != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli interní chybě",
                    internalError);
            return false;
        }

        return true;
    }

    private static String checkExternalErrors() {
        try {
            {
                Pair<Boolean, Response> result = tryMakeRequest("https://www.google.com", RequestMethod.GET);

                if (!result.first.booleanValue())
                    return "Nelze navázat spojení se stránkami.";

                if (getBodyOrNull(result) == null)
                    return "Nelze získat HTML stránek.";
            }
            if (getPageHtmlOrNull(1) == null)
                return "Nelze získat HTML mimibazar stránek.";

            if (getPageIdsOrEmpty(1, null).isEmpty())
                return "Nelze získat IDs položek na mimibazaru.";

            if (tryGetRemainingUpdates() == -1)
                return "Nelze získat zbývající aktualizace na mimibazaru.";

        } catch (Exception e) {
            Log.e("Updater", getExceptionAsString(e));
            return "Při testu externích chyb nastala neočekávaná chyba.";
        }

        return null;
    }

    private static String checkInternalErrors(Context context) {
        // If the CURR_ID_INDEX is not a digit, overwrite it to 50.
        try {Integer.parseInt(getPref(context, PREF_CURR_ID_INDEX, "0")); }
        catch (Exception e) { Log.e("Updater", getExceptionAsString(e)); writePref(context, PREF_CURR_ID_INDEX, "50"); }

        return null;
    }

    private static String execute(Context context) {
        // Initialization.
        int currIdIndex = Integer.parseInt(getPref(context, PREF_CURR_ID_INDEX, "0"));
        int remainingUpdates = tryGetRemainingUpdates();
        String[] ids = getIdsFromPrefs(context);

        // Checks.
        if (ids.length == 0 || ids.length == 1) {
            boolean created = tryRecreatePrefIds(context);

            if (!created || ids.length == 0 || ids.length == 1)
                return "Nelze vytvořit seznam ID položek z mimibazaru.";

            ids = getIdsFromPrefs(context);
        }

        Log.e("", "IDS LENGHT: " + ids.length);
        if (ids.length < 50)
            return "Seznam ID položek je příliš malý.";

        if (remainingUpdates == -1)
            return "Nelze získat zbývající aktualizace.";

        // Loop variables initialization.
        int iterationCount = 0;
        int photoUpdateErrorCount = 0;
        final int maxPhotoUpdateErrors = 5;
        final int maxIterations = 100;

        String error = null;
        while (remainingUpdates > 0 && ++iterationCount < maxIterations) {

            if (currIdIndex >= ids.length - 1) {
                currIdIndex = 0;
                if (!tryRecreatePrefIds(context)) {
                    error = "Nelze znovu-vytvořit seznam ID položek z mimibazaru.";
                    break;
                }

                ids = getIdsFromPrefs(context);
            }

            if (!tryUpdatePhoto(ids[currIdIndex])) {
                if (++photoUpdateErrorCount >= maxPhotoUpdateErrors) {
                    error = String.format("Při aktualizaci fotek nastala chyba více jak %s-krát.", maxPhotoUpdateErrors);
                    break;
                }
            } else {
                int remainingFromServer = tryGetRemainingUpdates();
                if (remainingFromServer == -1)
                    --remainingUpdates;
                else
                    remainingUpdates = remainingFromServer;
            }

            ++currIdIndex;
        }

        writePref(context, PREF_CURR_ID_INDEX, currIdIndex+"");
        return error;
    }

    // Returns -1 if can't get.
    private static int tryGetRemainingUpdates() {
        String url = "https://www.mimibazar.cz/bazar.php?user=106144";
        Pair<Boolean, Response> result = tryMakeRequest(url, RequestMethod.POST);

        if (!result.first.booleanValue())
            return -1;

        String html = getBodyOrNull(result);
        if (isEmptyOrNull(html))
            return -1;

        Matcher amountMatcher = UPDATES_AMOUNT_REGEX_PATTERN.matcher(html);
        if (amountMatcher.find()) {
            try {
                return Integer.parseInt(amountMatcher.group());
            } catch (NumberFormatException e) {
                Log.e("Updater", getExceptionAsString(e));
            }
        }

        if (UPDATES_ONE_REGEX_PATTERN.matcher(html).find())
            return 1;

        if (UPDATES_NONE_REGEX_PATTERN.matcher(html).find())
            return 0;

        return -1;
    }

    private static String[] getIdsFromPrefs(Context context) {
        String prefIds = getPref(context, PREF_IDS, "");
        Log.e("ids from prefs", prefIds);
        return prefIds.split(" ");
    }

    private static boolean tryRecreatePrefIds(Context context) {
        int amountOfPages = Integer.parseInt(getPref(context, PREF_AMOUNT_OF_PAGES, "25"));
        Set<String> newIds = createIdListOrEmpty(amountOfPages);
        if (newIds.isEmpty())
            return false;

        String newPrefIds = String.join(" ", newIds);
        writePref(context, PREF_IDS, newPrefIds);
        return true;
    }

    private static Set<String> createIdListOrEmpty(int amountOfPages) {
        Set<String> ids = new HashSet<>();
        // Iterate backwards because mimibazar puts already updated items at the
        // beginning so we won't update it twice.
        for (int i = amountOfPages; i > 0; --i)
            getPageIdsOrEmpty(i, ids);

        return ids;
    }

    private static boolean tryUpdatePhoto(String id) {
        String url = "https://www.mimibazar.cz/bazar.php?id=" + id + "&updfoto=ok";
        Pair<Boolean, Response> result = tryMakeRequest(url, RequestMethod.POST);
        return result.first.booleanValue();
    }

    // The body cannot be logged because it's too long.
    private static Set<String> getPageIdsOrEmpty(int page, @Nullable Set<String> set) {
        if (set == null)
            set = new HashSet<String>();

        String html = getPageHtmlOrNull(page);
        if (html == null)
            return set;

        Matcher m = ID_REGEX_PATTERN.matcher(html);
        while (m.find())
            set.add(m.group());

        return set;
    }

    private static @Nullable String getPageHtmlOrNull(int page) {
        String url = "https://www.mimibazar.cz/bazar.php?user=106144&strana=" + page;
        Pair<Boolean, Response> result = tryMakeRequest(url, RequestMethod.GET);

        return getBodyOrNull(result);
    }

    private static @Nullable String getBodyOrNull(Pair<Boolean, Response> result) {
        if (!result.first.booleanValue())
            return null;

        try {
            String body =  result.second.body().string();
            result.second.close();
            return body;
        } catch (Exception e) {
            Log.e("Updater", getExceptionAsString(e));
            return null;
        }
    }

    private static Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method) {
        while ((System.currentTimeMillis() - lastRequest) < REQUEST_THROTTLE_MS) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e("", getExceptionAsString(e));
            }
        }

        lastRequest = System.currentTimeMillis();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url);

        if (method == RequestMethod.POST) {
            requestBuilder.post(REQUEST_BODY);
            requestBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded");
        }
        else
            requestBuilder.get();

        Call call = HTTP_CLIENT.newCall(requestBuilder.build());
        Response response = null;
        try {
            response = call.execute();
        } catch (Exception e) {
            Log.e("Updater", getExceptionAsString(e));
        }

        return new Pair(response != null && response.isSuccessful(), response);
    }
}
