package com.gmail.matejpesl1.mimi;

import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;
import static com.gmail.matejpesl1.mimi.utils.Utils.isEmptyOrNull;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MimibazarRequester {
    private static final String TAG = MimibazarRequester.class.getSimpleName();

    // ?modal=user-login
    // Patterns.
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("(?<=href=\"https://www\\.mimibazar\\.cz/inzerat/)\\d+(?=/.*\")");
        // Matches the amount of remaining updates.
    private static final Pattern UPDATES_AMOUNT_PATTERN = Pattern.compile("(?<=Dostupné aktualizace\\s{0,400}<span class=\"text-orange\">\\s{0,400}\\()\\d+");
        // Matches the amount of maximal possible updates (no matter how many are remaining).
    private static final Pattern UPDATES_MAX_PATTERN = Pattern.compile("(?<=Dostupné aktualizace\\s{0,400}<span class=\"text-orange\">\\s{0,400}\\(\\d{0,400}/)\\d+");
        // Matches the user's ID.
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?<=<div class=\"user__id\">ID )\\d+(?=<\\/div>)");

    // Internet.
    private static final String MAIN_PAGE_URL = "https://www.mimibazar.cz/";
    private static final String BAZAR_BASE_URL = MAIN_PAGE_URL + "bazar.php?";
    private final Requester requester;
    private final RequestBody reqBody;
    private String profileUrl;

    // Other.
    public final String username;
    public final String password;

    public static class CouldNotGetAccIdException extends Exception {}

    public MimibazarRequester(Requester requester, String username, String password) {
        this.username = username;
        this.password = password;
        this.requester = requester;
        reqBody = new FormBody.Builder()
                .add("login", username)
                .add("password", password)
                .add("log_in", "ok")
                .build();
    }

    public void init() throws CouldNotGetAccIdException {
        // To log-in. Login will be preserved in subsequent requests.
        requester.tryMakeRequest("https://www.mimibazar.cz/?modal=user-login", Requester.RequestMethod.POST, reqBody);
        int userId = tryGetUserId();
        if (userId == -1)
            throw new CouldNotGetAccIdException();

        profileUrl = BAZAR_BASE_URL + "user=" + userId;
    }

    public int tryGetUserId() {
        String body = requester.getWebsiteBodyOrNull(MAIN_PAGE_URL, Requester.RequestMethod.GET);

        if (isEmptyOrNull(body)) {
            Log.e(TAG, "Could not get main page body (empty ot null)");
            return -1;
        }
        
        Matcher matcher = USER_ID_PATTERN.matcher(body);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                Log.e(TAG, getExAsStr(e));
            }
        }

        Log.e(TAG, "Could not find user id on website via regex.");
        return -1;
    }

    /** Returns a pair of <remaining, max> updates. */
    public Pair<Integer, Integer> getUpdatesState() {
        String pageBody = getPageBodyOrNull(1, true);

        return new Pair<>(tryGetRemainingUpdates(pageBody), tryGetMaxUpdates(pageBody));
    }

    public int tryGetRemainingUpdates(@Nullable String body) {
        if (isEmptyOrNull(body))
            body = requester.getWebsiteBodyOrNull(profileUrl, Requester.RequestMethod.GET);

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

        return -1;
    }

    public int tryGetMaxUpdates(@Nullable String body) {
        if (isEmptyOrNull(body))
            body = requester.getWebsiteBodyOrNull(profileUrl, Requester.RequestMethod.GET);

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
        Pair<Boolean, Response> result = requester.tryMakeRequest(url, Requester.RequestMethod.GET);
        return result.first;
    }

    public LinkedHashSet<String> getIdsFromPageOrEmpty(int page, @Nullable LinkedHashSet<String> list, @Nullable String body){
        if (list == null)
            list = new LinkedHashSet<>();

        if (isEmptyOrNull(body))
            body = getPageBodyOrNull(page, false);

        if (isEmptyOrNull(body))
            return list;

        // One ID is found on a site like 4 times, so we need a collection that only allows unique values,
        // so that every id is not duplicated 4x.
        Matcher m = ITEM_ID_PATTERN.matcher(body);
        while (m.find())
            list.add(m.group());

        return list;
    }

    public @Nullable String getPageBodyOrNull(int page, boolean loggedIn) {
        String url = profileUrl + "&strana=" + page;
        return requester.getWebsiteBodyOrNull(url, Requester.RequestMethod.GET, null, null, loggedIn);
    }
}
