package com.gmail.matejpesl1.mimi;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;
import static com.gmail.matejpesl1.mimi.utils.Utils.isEmptyOrNull;

public class MimibazarRequester {
    private static final String TAG = "MimibazarRequester";

    // Patterns.
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("(?<=href=\"https://www\\.mimibazar\\.cz/inzerat/)\\d+(?=/.*\")");
        // Matches the amount of remaining updates IF it's > 1 (because <= 1 needs a different pattern).
    private static final Pattern UPDATES_AMOUNT_PATTERN = Pattern.compile("(?<=Stále lze využít )\\d+(?= aktualiza)");
        // Matches if we can still use one update.
    private static final Pattern UPDATES_ONE_PATTERN = Pattern.compile("Stále lze využít jednu aktualiza");
        // Matches if we can't use any updates.
    private static final Pattern UPDATES_NONE_PATTERN = Pattern.compile("Dnes jste využili všechny aktualizace");
        // Matches the amount of maximal possible updates (no matter how many are remaining).
    private static final Pattern UPDATES_MAX_PATTERN = Pattern.compile("(?<=Denně můžete aktualizovat )\\d+(?= inzertních)");
        // Matches the user's ID.
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?<=<div class=\"user__id\">ID )\\d+(?=</div>)");

    // Internet.
    private final static String MAIN_PAGE_URL = "https://www.mimibazar.cz/";
    private final static String BAZAR_BASE_URL = MAIN_PAGE_URL + "bazar.php?";
    private final Requester requester;
    private final RequestBody reqBody;
    private final String profileUrl;

    // Other.
    public final String username;
    public final String password;

    public static class CouldNotGetAccIdException extends Exception {}

    public MimibazarRequester(Requester requester, String username, String password) throws CouldNotGetAccIdException {
        this.username = username;
        this.password = password;
        this.requester = requester;
        reqBody = buildRequestBody(username, password);

        int userId = tryGetUserId();
        if (userId == -1)
            throw new CouldNotGetAccIdException();

        profileUrl = BAZAR_BASE_URL + "user=" + userId;
    }

    public int tryGetUserId() {
        String body = requester.getWebsiteBodyOrNull(
                MAIN_PAGE_URL,
                Requester.RequestMethod.POST,
                reqBody);

        if (isEmptyOrNull(body))
            return -1;

        Matcher matcher = USER_ID_PATTERN.matcher(body);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                Log.e(TAG, getExAsStr(e));
            }
        }

        return -1;
    }

    /** Returns a pair of <remaining, max> updates. */
    public Pair<Integer, Integer> getUpdatesState() {
        String pageBody = getPageBodyOrNull(1, true);

        return new Pair<>(tryGetRemainingUpdates(pageBody), tryGetMaxUpdates(pageBody));
    }

    private static RequestBody buildRequestBody(String username, String password) {
        return new FormBody.Builder()
                .add("login", username)
                .add("password", password)
                .add("log_in", "ok")
                .build();
    }

    public int tryGetRemainingUpdates(@Nullable String body) {
        if (isEmptyOrNull(body))
            body = requester.getWebsiteBodyOrNull(profileUrl, Requester.RequestMethod.POST, reqBody);

        if (isEmptyOrNull(body))
            return -1;

        Matcher amountMatcher = UPDATES_AMOUNT_PATTERN.matcher(body);
        if (amountMatcher.find()) {
            try {
                return Integer.parseInt(amountMatcher.group());
            } catch (NumberFormatException e) {
                Log.e(TAG, getExAsStr(e));
            }
        }

        if (UPDATES_ONE_PATTERN.matcher(body).find())
            return 1;

        if (UPDATES_NONE_PATTERN.matcher(body).find())
            return 0;

        return -1;
    }

    public int tryGetMaxUpdates(@Nullable String body) {
        if (isEmptyOrNull(body))
            body = requester.getWebsiteBodyOrNull(profileUrl, Requester.RequestMethod.POST, reqBody);

        if (isEmptyOrNull(body))
            return -1;

        Matcher matcher = UPDATES_MAX_PATTERN.matcher(body);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                Log.e(TAG, getExAsStr(e));
            }
        }

        return -1;
    }

    public boolean tryUpdatePhoto(String id) {
        String url = BAZAR_BASE_URL + "id=" + id + "&updfoto=ok";
        Pair<Boolean, Response> result = requester.tryMakeRequest(
                url,
                Requester.RequestMethod.POST,
                reqBody);

        return result.first;
    }

    // The body cannot be logged because it's too long.
    public ArrayList<String> getIdsFromPageOrEmpty(int page, @Nullable ArrayList<String> list, @Nullable String body) {
        if (list == null)
            list = new ArrayList<>();

        if (isEmptyOrNull(body))
            body = getPageBodyOrNull(page, false);

        if (isEmptyOrNull(body))
            return list;

        Matcher m = ITEM_ID_PATTERN.matcher(body);
        while (m.find())
            list.add(m.group());

        return list;
    }

    public @Nullable String getPageBodyOrNull(int page, boolean loggedIn) {
        String url = profileUrl + "&strana=" + page;
        return loggedIn
                ? requester.getWebsiteBodyOrNull(url, Requester.RequestMethod.POST, reqBody)
                : requester.getWebsiteBodyOrNull(url, Requester.RequestMethod.GET, null);
    }
}
