package com.gmail.matejpesl1.mimi;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.HashSet;
import java.util.Set;
import okhttp3.Response;

import static com.gmail.matejpesl1.mimi.utils.Utils.*;

public class Updater {
    // Prefs
    private static final String PREF_IDS = "IDs Of Items";
    private static final String PREF_AMOUNT_OF_PAGES = "Amount Of Pages To Update";
    private static final String PREF_CURR_ID_INDEX = "Index Of Current ID";
    private static final String PREF_NOTIFY_ABOUT_SUCCESFULL_UPDATE = "Notify About Sucesfull update";
    private static final String PREF_USERNAME = "Username";
    private static final String PREF_PASSWORD = "Password";

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

    // Notify about succesfull update
    public static void setNotifyAboutSuccesfullUpdate(Context context, boolean notify) {
        Utils.writePref(context, PREF_NOTIFY_ABOUT_SUCCESFULL_UPDATE, notify+"");
    }
    public static boolean getNotifyAboutSuccesfullUpdate(Context context) {
        return Boolean.parseBoolean(
                Utils.getPref(context, PREF_NOTIFY_ABOUT_SUCCESFULL_UPDATE, "true"));
    }

    // Amount of Updated pages
    public static void setAmountOfUpdatedPages(Context context, int amount) {
        writePref(context, PREF_AMOUNT_OF_PAGES, amount+"");
    }
    public static int getAmountOfUpdatedPages(Context context) {
        return Integer.parseInt(getPref(context, PREF_AMOUNT_OF_PAGES, "25"));
    }

    // Credentials
    public static void setCredentials(Context context, String username, String password) {
        writePref(context, PREF_USERNAME, username);
        writePref(context, PREF_PASSWORD, password);
    }
    public static String getPassword(Context context) {
        return getPref(context, PREF_PASSWORD, "");
    }
    public static String getUsername(Context context) {
        return getPref(context, PREF_USERNAME, "");
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
            Notifications.PostDefaultNotification(context,
                    context.getResources().getString(R.string.cannot_update_mimibazar_runtime_error),
                    error);
        } else if (getNotifyAboutSuccesfullUpdate(context))
            Notifications.PostDefaultNotification(context,
                    context.getResources().getString(R.string.mimibazar_sucesfully_updated),
                    "");
    }

    private boolean initAndNotifyIfError() {
        requester = new Requester(REQUEST_THROTTLE);
        String username = getPref(context, PREF_USERNAME, "");
        String password = getPref(context, PREF_PASSWORD, "");
        if (isEmptyOrNull(username) || isEmptyOrNull(password)) {
            Notifications.PostDefaultNotification(context,
                    context.getResources().getString(R.string.cannot_update_mimibazar),
                    context.getResources().getString(R.string.missing_credentials));
            Log.w(TAG, "Credentials are missing, cannot update. Returning.");
            return false;
        }

        try {
            mimibazarRequester = new MimibazarRequester(requester, username, password);
        } catch (MimibazarRequester.CouldNotGetAccIdException e) {
            Notifications.PostDefaultNotification(context,
                    context.getResources().getString(R.string.cannot_update_mimibazar),
                    context.getResources().getString(R.string.cannot_update_invalid_credentials_or_external_error));
            Log.w(TAG, "MimibazarRequester cannot be created - cannot get account id. Returning.");
            return false;
        }

        return true;
    }

    private boolean makeChecksAndNotifyAboutErrors() {
        String externalError = checkExternalErrors();
        if (externalError != null) {
            Log.e(TAG, "External error encountered. Error: " + externalError);
            Notifications.PostDefaultNotification(
                    context,
                    context.getResources().getString(R.string.cannot_update_mimibazar_external_error),
                    externalError);
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
        int currIdIndex = Integer.parseInt(getPref(context, PREF_CURR_ID_INDEX, "0"));
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
                writePref(context, PREF_CURR_ID_INDEX, currIdIndex+"");
            }
        }

        writePref(context, PREF_CURR_ID_INDEX, currIdIndex+"");
        return error;
    }

    private String[] getIdsFromPrefs() {
        String prefIds = getPref(context, PREF_IDS, "");
        return prefIds.split(" ");
    }

    private boolean tryRecreatePrefIds() {
        int amountOfPages = getAmountOfUpdatedPages(context);
        Set<String> newIds = createIdListOrEmpty(amountOfPages);

        if (newIds.size() < 3)
            return false;

        String newPrefIds = String.join(" ", newIds);
        writePref(context, PREF_IDS, newPrefIds);
        return true;
    }

    private Set<String> createIdListOrEmpty(int amountOfPages) {
        Set<String> ids = new HashSet<>();
        // Iterate backwards because mimibazar puts already updated items at the
        // beginning so we won't update it twice.
        for (int i = amountOfPages; i > 0; --i)
            mimibazarRequester.getIdsFromPageOrEmpty(i, ids, null);

        return ids;
    }
}
