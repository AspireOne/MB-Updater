package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.Nullable;

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
    private static final String PREF_IDS = "IDs Of Items";
    private static final String PREF_AMOUNT_OF_PAGES = "Amount Of Pages To Update";
    private static final String PREF_CURR_ID_INDEX = "Index Of Current ID";
    private static final Pattern ID_REGEX_PATTERN = Pattern.compile("(?<=href=\"https:\\/\\/www\\.mimibazar\\.cz\\/inzerat\\/)\\d+(?=\\/.*\")");
    private static final Pattern UPDATES_AMOUNT_REGEX_PATTERN = Pattern.compile("(?<=Stále lze využít ).*(?= aktualiza)");
    private static final Pattern UPDATES_NONE_REGEX_PATTERN = Pattern.compile("Dnes jste využili všechny aktualizace");
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

    public static void update(Context context) {
        if (!makeChecksAndNotifyAboutErrors(context))
            return;

        String error = execute(context);

        if (error != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli runtime chybě",
                    error);
        }
    }

    private static boolean makeChecksAndNotifyAboutErrors(Context context) {
        String externalError = checkExternalErrors();

        if (externalError != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli externí chybě",
                    externalError);
            return true;
        }

        String internalError = checkInternalErrors(context);
        if (internalError != null) {
            Notifications.PostDefaultNotification(
                    context,
                    "Nelze aktualizovat Mimibazar kvůli interní chybě",
                    internalError);
            return true;
        }

        return false;
    }

    private static String checkExternalErrors() {
        try {
            {
                Pair<Boolean, Response> result = tryMakeRequest("https://www.google.com", RequestMethod.GET);

                if (result.first.booleanValue())
                    return "Nelze navázat spojení se stránkami.";

                if (getBodyOrNull(result) == null)
                    return "Nelze získat HTML stránek.";
            }
            if (getPageHtmlOrNull(1) == null)
                return "Nelze získat HTML mimibazar stránek.";

            if (getPageIdsOrEmpty(1, null).isEmpty())
                return "Nelze získat IDs položek na mimibazaru.";

            if (getRemainingUpdates() == -1)
                return "Nelze získat zbývající aktualizace na mimibazaru.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Při testu externích chyb nastala neočekávaná chyba.";
        }

        return null;
    }

    private static String checkInternalErrors(Context context) {
        try {Integer.parseInt(getPref(context, PREF_CURR_ID_INDEX, "0")); }
        catch (Exception e) { e.printStackTrace(); writePref(context, PREF_CURR_ID_INDEX, "50"); }

        return null;
    }

    private static String execute(Context context) {
        int currIdIndex = Integer.parseInt(getPref(context, PREF_CURR_ID_INDEX, "0"));
        String[] ids = getIdsFromPrefs(context);

        if (ids.length == 0) {
            boolean couldRecreate = tryRecreatePrefIds(context);

            if (!couldRecreate)
                return "Nelze vytvořit seznam ID položek z mimibazaru.";

            ids = getIdsFromPrefs(context);
        }

        for (; currIdIndex < ids.length; ++currIdIndex) {
            tryUpdatePhoto(ids[currIdIndex]);
        }


    }

    private static int getRemainingUpdates() {
        String url = "https://www.mimibazar.cz/bazar.php?user=106144";
        Pair<Boolean, Response> result = tryMakeRequest(url, RequestMethod.POST);

        if (!result.first)
            return -1;

        String html = getBodyOrNull(result);
        if (isEmptyOrNull(html))
            return -1;

        String amount = UPDATES_AMOUNT_REGEX_PATTERN.matcher(html).group(1);
        if (!isEmptyOrNull(amount)) {
            try {
                return Integer.parseInt(amount);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        if (UPDATES_NONE_REGEX_PATTERN.matcher(html).matches())
            return 0;

        return -1;
    }

    private static String[] getIdsFromPrefs(Context context) {
        String prefIds = getPref(context, PREF_IDS, "");
        return prefIds.split("\\r?\\n");
    }

    private static boolean tryRecreatePrefIds(Context context) {
        int amountOfPages = Integer.parseInt(getPref(context, PREF_AMOUNT_OF_PAGES, "25"));
        Set<String> newIds = createIdListOrEmpty(amountOfPages);
        String newPrefIds = String.join("\n", newIds);
        writePref(context, PREF_IDS, newPrefIds);

        return newPrefIds.isEmpty();
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
        return result.first;
    }

    // The body cannot be logged because it's too long.
    private static Set<String> getPageIdsOrEmpty(int page, @Nullable Set<String> set) {
        Set<String> allMatches = set == null ? new HashSet<>() : set;

        String html = getPageHtmlOrNull(page);
        if (html == null)
            return allMatches;

        Matcher m = ID_REGEX_PATTERN.matcher(html);
        while (m.find())
            allMatches.add(m.group());

        return allMatches;
    }

    private static @Nullable String getPageHtmlOrNull(int page) {
        String url = "https://www.mimibazar.cz/bazar.php?user=106144&strana=" + page;
        Pair<Boolean, Response> result = tryMakeRequest(url, RequestMethod.GET);

        return getBodyOrNull(result);
    }

    private static @Nullable String getBodyOrNull(Pair<Boolean, Response> result) {
        if (!result.first)
            return null;

        try {
            return result.second.body().string();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Pair<Boolean, Response> tryMakeRequest(String url, RequestMethod method) {
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
            e.printStackTrace();
        }

        return new Pair(response != null && response.isSuccessful(), response);
    }
}