package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.ArrayList;
import okhttp3.Response;

import static com.gmail.matejpesl1.mimi.utils.Utils.*;

public class Updater {
    // Prefs
    private static final String PREF_IDS = "ids_of_items";
    private static final String PREF_CURR_ID_INDEX = "index_of_current_id";

    // Other
    private static final String TAG = "Updater";
    private static final int REQUEST_THROTTLE = 300;
    private static boolean running = false;
    private Requester requester;
    private MimibazarRequester mimibazarRequester;
    private Context context;

    public Updater(Context context) {
        this.context = context;
    }

    /*public static boolean tryForceRecreateIdList(Context context) {
        boolean recreated = tryRecreatePrefIds(context);
        if (!recreated)
            return false;

        writePref(context, PREF_CURR_ID_INDEX, "0");
        return true;
    }*/

    public void update() {
        if (running) {
            Log.w(TAG, "Updater is already running, returning.");
            return;
        }

        running = true;
        prepareAndExecute();
        running = false;
    }

    private void prepareAndExecute() {
        // This method first, because it initializes the requester.
        if (!initAndNotifyIfError())
            return;

        if (mimibazarRequester.tryGetRemainingUpdates(null) == 0) {
            Log.w(TAG, "Mimibazar was attempted to be updated but it has already " +
                    "0 remaining updates. Returning.");
            return;
        }

        if (!makeChecksAndNotifyAboutErrors())
            return;

        Log.i(TAG, "All pre-update checks passed.");
        String error = execute();
        Log.i(TAG, "Update finished. Error (if any): " + error);

        if (error != null) {
            Notifications.postNotification(context, R.string.mimibazar_cannot_update_desc_runtime_error,
                    error, Notifications.Channel.ERROR);
        } else if (getBooleanPref(context, R.string.setting_successful_update_notification_key, true)) {
            Notifications.postNotification(context, R.string.mimibazar_sucesfully_updated, "",
                    Notifications.Channel.DEFAULT);
        }
    }

    private boolean initAndNotifyIfError() {
        requester = new Requester(REQUEST_THROTTLE);
        String username = getStringPref(context, R.string.setting_username_key, "");
        String password = getStringPref(context, R.string.setting_password_key, "");
        if (isEmptyOrNull(username) || isEmptyOrNull(password)) {
            Notifications.postNotification(context, R.string.mimibazar_cannot_update,
                    R.string.missing_credentials, Notifications.Channel.ERROR);
            Log.w(TAG, "Credentials are missing, cannot update. Returning.");
            return false;
        }

        try {
            mimibazarRequester = new MimibazarRequester(requester, username, password);
        } catch (MimibazarRequester.CouldNotGetAccIdException e) {
            Notifications.postNotification(context, R.string.mimibazar_cannot_update,
                    R.string.mimibazar_cannot_update_desc_invalid_credentials, Notifications.Channel.ERROR);
            Log.e(TAG, "MimibazarRequester cannot be created - cannot get account id. Returning.");
            return false;
        }

        return true;
    }

    private boolean makeChecksAndNotifyAboutErrors() {
        String externalError = checkExternalErrors();
        if (externalError != null) {
            Log.e(TAG, "External error encountered. Error: " + externalError);
            Notifications.postNotification(context, R.string.mimibazar_cannot_update_desc_external_error,
                    externalError, Notifications.Channel.ERROR);
            return false;
        }

        /*String internalError = checkInternalErrors(context);
        if (internalError != null) {
            Log.e(TAG, "Internal error encountered. Error: " + internalError);
            Notifications.PostDefaultNotification(
                    context,
                    context.getResources().getString(R.string.cannot_update_mimibazar_internal_error),
                    internalError);
            return false;
        }*/

        return true;
    }

    private String checkExternalErrors() {
        try {
            {
                Pair<Boolean, Response> result = requester.tryMakeRequest(
                        "https://www.google.com",
                        Requester.RequestMethod.GET,
                        null);

                if (!result.first.booleanValue())
                    return "Nelze navázat spojení se stránkami.";

                if (Utils.isEmptyOrNull(requester.getBodyOrNull(result)))
                    return "Nelze získat HTML stránek.";
            }

            String mimibazarPageBody = mimibazarRequester.getPageBodyOrNull(1, true);

            if (Utils.isEmptyOrNull(mimibazarPageBody))
                return "Nelze získat HTML mimibazar stránek.";

            if (mimibazarRequester.getIdsFromPageOrEmpty(1, null, mimibazarPageBody).isEmpty())
                return "Nelze získat ID položek na mimibazaru.";

            if (mimibazarRequester.tryGetRemainingUpdates(mimibazarPageBody) == -1)
                return "Nelze získat zbývající aktualizace na mimibazaru.";

        } catch (Exception e) {
            Log.e(TAG, getExceptionAsString(e));
            return "Při testu externích chyb nastala neočekávaná chyba.";
        }

        return null;
    }

    private String execute() {
        // Initialization.
        int currIdIndex = getIntPref(context, PREF_CURR_ID_INDEX, 0);
        int remainingUpdates = mimibazarRequester.tryGetRemainingUpdates(null);
        String[] ids = getIdsFromPrefs();

        // Checks.
        if (remainingUpdates == -1)
            return "Nelze získat zbývající aktualizace.";

        if (ids.length == 0 || ids.length == 1) {
            boolean created = tryRecreatePrefIds();

            ids = getIdsFromPrefs();
            if (!created || ids.length == 0 || ids.length == 1)
                return "Nelze vytvořit seznam ID položek z mimibazaru.";
        }

        Log.i(TAG, "ID list has " + ids.length + " items.");

        // Loop variables initialization.
        int iterationCount = 0;
        int photoUpdateErrorCount = 0;
        int lineSaveCount = 0;
        final int maxPhotoUpdateErrors = 10;
        final int maxIterations = 300;
        // Save the current line to preferences every x seconds to prevent loss in case of force close.
        final int lineSaveFreq = 9;

        String error = null;
        while (remainingUpdates > 0 && ++iterationCount < maxIterations) {
            if (currIdIndex >= ids.length - 1) {
                currIdIndex = 0;
                if (!tryRecreatePrefIds()) {
                    error = "Nelze znovu-vytvořit seznam ID položek z mimibazaru.";
                    break;
                }

                ids = getIdsFromPrefs();
            }

            if (!mimibazarRequester.tryUpdatePhoto(ids[currIdIndex])) {
                Log.e(TAG, "Při aktualizaci fotky nastala chyba.");
                if (++photoUpdateErrorCount >= maxPhotoUpdateErrors) {
                    error = String.format("Při aktualizaci fotek nastala chyba více jak %s-krát.", maxPhotoUpdateErrors);
                    break;
                }
            } else {
                int remainingFromServer = mimibazarRequester.tryGetRemainingUpdates(null);
                if (remainingFromServer == -1) {
                    Log.e(TAG, "Could not get remaining updates from server.");
                    --remainingUpdates;
                }
                else
                    remainingUpdates = remainingFromServer;
            }

            ++currIdIndex;

            if (++lineSaveCount >= lineSaveFreq) {
                lineSaveCount = 0;
                writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
            }
        }

        writePref(context, PREF_CURR_ID_INDEX, currIdIndex);
        return error;
    }

    private String[] getIdsFromPrefs() {
        String prefIds = getStringPref(context, PREF_IDS, "");
        return prefIds.split(" ");
    }

    private boolean tryRecreatePrefIds() {
        int amountOfPages = getIntPref(context, R.string.setting_pages_amount_key, 25);
        ArrayList<String> newIds = createIdListOrEmpty(amountOfPages);

        if (newIds.size() < 3)
            return false;

        String newPrefIds = String.join(" ", newIds);
        writePref(context, PREF_IDS, newPrefIds);
        return true;
    }

    private ArrayList<String> createIdListOrEmpty(int amountOfPages) {
        ArrayList<String> ids = new ArrayList<>();
        // Iterate backwards because mimibazar puts already updated items at the
        // beginning so we won't update it twice.
        for (int i = amountOfPages; i > 0; --i)
            mimibazarRequester.getIdsFromPageOrEmpty(i, ids, null);

        return ids;
    }
}
