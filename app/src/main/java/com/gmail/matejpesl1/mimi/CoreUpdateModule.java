package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.LinkedHashSet;

import static com.gmail.matejpesl1.mimi.utils.Utils.getIntPref;

public class CoreUpdateModule {
    // Prefs
    private static final String PREF_CURR_ID_INDEX = "index_of_current_id";
    private static final String PREF_IDS = "ids_of_items";
    // Other
    private static final String TAG = "CoreUpdateModule";

    private static final int MAX_PHOTO_UPDATE_ERRORS = 10;
    private static final int MAX_ITERATIONS = 300;

    private final MimibazarRequester mimibazarRequester;
    private final Context context;

    // All variables below are initialized to their initial value, and are changed during the
    // update process.
    private int remainingUpdates;
    private int currIdIndex;
    private String[] ids;

    private int photoUpdateErrors = 0;
    private int iterations = 0;

    public CoreUpdateModule(Context context, MimibazarRequester mimibazarRequester) {
        this.context = context;
        this.mimibazarRequester = mimibazarRequester;

        remainingUpdates = mimibazarRequester.tryGetRemainingUpdates(null);
        currIdIndex = getIntPref(context, PREF_CURR_ID_INDEX, 0);
        ids = getIdsFromPrefsAndTryAssertCorrectOrNull();
    }

    public String execute() {
        if (ids == null)
            return "Nelze vytvořit seznam ID položek z mimibazaru.";

        Log.i(TAG, "Beginning main update loop. ID list length: " + ids.length
                + " | remaining updates: " + remainingUpdates
                + " | current ID index: " + currIdIndex);

        String error = executeUpdateLoop();

        Log.i(TAG, String.format("Update finished. Iterations: %s. currIdIndex: %s. PhotoUpdate" +
                "Errors: %s.", iterations, currIdIndex, photoUpdateErrors));

        Utils.writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
        return error;
    }

    private String executeUpdateLoop() {
        while (remainingUpdates > 0 && iterations++ < MAX_ITERATIONS) {
            if (currIdIndex >= ids.length - 1 && !tryRecreateIds())
                return "Nelze znovu-vytvořit seznam ID položek z mimibazaru.";

            if (!mimibazarRequester.tryUpdatePhoto(ids[currIdIndex])) {
                String error = handleUnsuccessfulPhotoUpdate();
                if (error != null)
                    return error;
            }
            else if (--remainingUpdates <= 2)
                remainingUpdates = mimibazarRequester.tryGetRemainingUpdates(null);

            ++currIdIndex;

            if (remainingUpdates % 5 == 0)
                Utils.writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
        }

        return null;
    }

    private String handleUnsuccessfulPhotoUpdate() {
        Log.w(TAG, "Could not update photo with ID " + ids[currIdIndex]);

        if (++photoUpdateErrors >= MAX_PHOTO_UPDATE_ERRORS)
            return String.format("Při aktualizaci fotek nastala chyba více jak %s-krát.", MAX_PHOTO_UPDATE_ERRORS);

        if (InternetUtils.isConnectionAvailable())
            return null;

        Utils.sleep(TAG, 15000);
        if (InternetUtils.isConnectionAvailable())
            return null;

        return "Při aktualizování přestalo být dostupné připojení.";
    }

    private boolean tryRecreateIds() {
        Log.i(TAG, "Reached the end of ID list. Recreating.");

        currIdIndex = 0;
        if (!tryRecreatePrefIds()) {
            Log.e(TAG, "Could not recreate ID list. Ending.");
            return false;
        }

        Log.i(TAG, "Recreated ID list. Size: " + ids.length + ". Continuing.");
        ids = getIdsFromPrefs();
        return true;
    }

    private String[] getIdsFromPrefsAndTryAssertCorrectOrNull() {
        String[] ids = getIdsFromPrefs();

        if (ids.length < 3) {
            if (tryRecreatePrefIds())
                return getIdsFromPrefs();

            return null;
        }

        return ids;
    }

    private String[] getIdsFromPrefs() {
        String prefIds = Utils.getPref(context, PREF_IDS, "");
        return prefIds.split(" ");
    }

    private boolean tryRecreatePrefIds() {
        int amountOfPages = getIntPref(context, R.string.setting_pages_amount_key, 25);
        LinkedHashSet<String> newIds = createIdListOrEmpty(amountOfPages);

        Utils.writePref(context, PREF_IDS, TextUtils.join(" ", newIds));

        return newIds.size() > 2;
    }

    private LinkedHashSet<String> createIdListOrEmpty(int amountOfPages) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        // Iterate backwards because mimibazar puts already updated items at the
        // beginning so we won't update it twice.
        for (int i = amountOfPages; i > 0; --i)
            mimibazarRequester.getIdsFromPageOrEmpty(i, ids, null);

        return ids;
    }
}
