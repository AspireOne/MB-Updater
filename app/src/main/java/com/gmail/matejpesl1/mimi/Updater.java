package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.LinkedHashSet;

import static com.gmail.matejpesl1.mimi.utils.Utils.getIntPref;

public class Updater {
    private static final String TAG = Updater.class.getSimpleName();
    private static final String PREF_CURR_ID_INDEX = "index_of_current_id";
    private static final String PREF_IDS = "ids_of_items";
    private static final int MAX_PHOTO_UPDATE_ERRORS = 10;
    private static final int RECONNECT_CHECK_DELAY = 15000;
    private static final int MAX_ITERATIONS = 300;

    private final ProgressNotification progressNotification;
    private final MimibazarRequester mimibazarRequester;
    private final Context context;

    // All variables below are initialized to their initial value, and are changed during the
    // update process.
    private int photoUpdateErrors = 0;
    private int remainingUpdates;
    private int iterations = 0;
    private int currIdIndex;
    private String[] ids;

    public Updater(Context context, MimibazarRequester mimibazarRequester) {
        this.context = context;
        this.mimibazarRequester = mimibazarRequester;

        remainingUpdates = mimibazarRequester.tryGetRemainingUpdates(null);
        progressNotification = new ProgressNotification("Probíhá aktualizace...", getFormattedRemainingText(), remainingUpdates, context);
        currIdIndex = getIntPref(context, PREF_CURR_ID_INDEX, 0);
        ids = getIdListFromPrefs();

        if (ids.length < 3)
            ids = tryRecreateAndSavePrefIdList() ? getIdListFromPrefs() : null;
    }

    public String startExecute() {
        progressNotification.start();
        String result = execute();
        progressNotification.cancel();
        return result;
    }

    private String execute() {
        if (ids == null)
            return "Nelze vytvořit seznam ID položek z mimibazaru.";

        Log.i(TAG, "Beginning main update loop. ID list length: " + ids.length + " | remaining updates: " + remainingUpdates + " | current ID index: " + currIdIndex);

        while (remainingUpdates > 0 && iterations++ < MAX_ITERATIONS) {
            if (currIdIndex >= ids.length - 1 && !tryRecreateIds())
                return "Nelze znovu-vytvořit seznam ID položek z mimibazaru.";

            if (!mimibazarRequester.tryUpdatePhoto(ids[currIdIndex])) {
                String error = handleUnsuccessfulPhotoUpdate();
                if (error != null) return error;
            }
            else if (--remainingUpdates <= 2)
                remainingUpdates = mimibazarRequester.tryGetRemainingUpdates(null);

            ++currIdIndex;
            progressNotification.updateProgress(progressNotification.max - remainingUpdates, getFormattedRemainingText());

            // Autosave in case it's force closed.
            if (remainingUpdates % 5 == 0)
                Utils.writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
        }

        progressNotification.cancel();
        Utils.writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
        Log.i(TAG, String.format("Update finished. Iterations: %s. currIdIndex: %s. PhotoUpdate" + "Errors: %s.", iterations, currIdIndex, photoUpdateErrors));

        return null;
    }

    private String getFormattedRemainingText() {
         final String text = remainingUpdates >= 5 || remainingUpdates == 0
                ? "Zbývá %s položek": remainingUpdates >= 2
                ? "Zbývají %s položky" : "Zbývá %s položka";

         return String.format(text, remainingUpdates);
    }

    private String handleUnsuccessfulPhotoUpdate() {
        Log.w(TAG, "Could not update photo with ID " + ids[currIdIndex]);

        if (++photoUpdateErrors >= MAX_PHOTO_UPDATE_ERRORS)
            return String.format("Při aktualizaci fotek nastala chyba více jak %s-krát.", MAX_PHOTO_UPDATE_ERRORS);

        if (InternetUtils.isConnectionAvailable())
            return null;

        Utils.sleep(TAG, RECONNECT_CHECK_DELAY);
        if (InternetUtils.isConnectionAvailable())
            return null;

        return "Při aktualizování přestalo být dostupné připojení.";
    }

    private boolean tryRecreateIds() {
        Log.i(TAG, "Reached the end of ID list. Recreating.");

        currIdIndex = 0;
        if (!tryRecreateAndSavePrefIdList()) {
            Log.e(TAG, "Could not recreate ID list. Ending.");
            return false;
        }

        Log.i(TAG, "Recreated ID list. Size: " + ids.length + ". Continuing.");
        ids = getIdListFromPrefs();
        return true;
    }

    private boolean tryRecreateAndSavePrefIdList() {
        int amountOfPages = getIntPref(context, R.string.setting_pages_amount_key, 25);
        LinkedHashSet<String> newIdList = fetchIdListOrEmpty(amountOfPages);

        Utils.writePref(context, PREF_IDS, TextUtils.join(" ", newIdList));
        return newIdList.size() > 2;
    }

    private String[] getIdListFromPrefs() {
        String prefIds = Utils.getPref(context, PREF_IDS, "");
        return prefIds.split(" ");
    }

    private LinkedHashSet<String> fetchIdListOrEmpty(int amountOfPages) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        // Iterate backwards because mimibazar puts already updated items at the
        // beginning so we won't update it twice.
        for (int i = amountOfPages; i > 0; --i)
            mimibazarRequester.getIdsFromPageOrEmpty(i, ids, null);

        return ids;
    }
}
