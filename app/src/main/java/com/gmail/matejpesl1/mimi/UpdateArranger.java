package com.gmail.matejpesl1.mimi;

import static com.gmail.matejpesl1.mimi.utils.Utils.getBooleanPref;
import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;
import static com.gmail.matejpesl1.mimi.utils.Utils.getPref;
import static com.gmail.matejpesl1.mimi.utils.Utils.isEmptyOrNull;
import static com.gmail.matejpesl1.mimi.utils.Utils.writePref;

import android.content.Context;
import android.util.Log;

import androidx.core.util.Pair;

import com.gmail.matejpesl1.mimi.utils.Utils;

import okhttp3.Response;

public class UpdateArranger {
    private static final String TAG = UpdateArranger.class.getSimpleName();
    private static final String PREF_RUNNING = "updater_running";
    private static final int REQUEST_THROTTLE = 300;
    private Requester requester;
    private MimibazarRequester mimibazarRequester;
    private final Context context;

    public UpdateArranger(Context context) {
        this.context = context;
    }

    /*public static boolean tryForceRecreateIdList(Context context) {
        boolean recreated = tryRecreatePrefIds(context);
        if (!recreated)
            return false;

        writePref(context, PREF_CURR_ID_INDEX, "0");
        return true;
    }*/

    public void arrangeAndUpdate() {
        if (getBooleanPref(context, PREF_RUNNING, false)) {
            Log.w(TAG, "Updater is already running, returning.");
            return;
        }

        writePref(context, PREF_RUNNING, true);

        if (!initAndCheck()) {
            writePref(context, PREF_RUNNING, false);
            return;
        }

        String error;

        try {
            error = new Updater(context, mimibazarRequester).startExecute();
        }  catch (Exception e) {
            error = "Neošetřená vyjímka při aktualizaci.";
        }
        Log.i(TAG, "Update finished. Error (if any): " + error);

        if (error != null)
            Notifications.postNotification(context, R.string.mimibazar_cannot_update_desc_runtime_error, error, Notifications.Channel.ERROR);
        else if (getBooleanPref(context, R.string.setting_successful_update_notification_key, true))
            Notifications.postNotification(context, R.string.mimibazar_sucesfully_updated, "", Notifications.Channel.DEFAULT);

        writePref(context, PREF_RUNNING, false);
    }

    private boolean initAndCheck() {
        // This method first, because it initializes the requester.
        if (!initAndNotifyIfError())
            return false;

        if (mimibazarRequester.tryGetRemainingUpdates(null) == 0) {
            Log.w(TAG, "Mimibazar was attempted to be updated but it has no remaining updates.");
            return false;
        }

        if (!makeChecksAndNotifyIfError())
            return false;

        Log.i(TAG, "All pre-update checks passed.");
        return true;
    }

    private boolean initAndNotifyIfError() {
        requester = new Requester(REQUEST_THROTTLE);
        String username = getPref(context, R.string.setting_username_key, "");
        String password = getPref(context, R.string.setting_password_key, "");
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

    private boolean makeChecksAndNotifyIfError() {
        String externalError = checkExternalErrors();
        if (externalError != null) {
            Log.e(TAG, "External error encountered. Error: " + externalError);
            Notifications.postNotification(context, R.string.mimibazar_cannot_update_desc_external_error, externalError,
                    Notifications.Channel.ERROR);
            return false;
        }

        return true;
    }

    private String checkExternalErrors() {
        try {
            // General test.
            {
                Pair<Boolean, Response> result = requester.tryMakeRequest("https://www.google.com",
                        Requester.RequestMethod.GET, null);

                if (!result.first)
                    return "Nelze navázat spojení se stránkami.";

                if (Utils.isEmptyOrNull(Requester.getBodyOrNull(result)))
                    return "Nelze získat obsah stránek.";
            }
            // Mimibazar test.
            {
                String mimibazarPageBody = mimibazarRequester.getPageBodyOrNull(1, true);

                if (Utils.isEmptyOrNull(mimibazarPageBody))
                    return "Nelze získat obsah mimibazar stránek.";

                if (mimibazarRequester.getIdsFromPageOrEmpty(1, null, mimibazarPageBody).isEmpty())
                    return "Nelze získat ID položek na mimibazaru.";

                if (mimibazarRequester.tryGetRemainingUpdates(mimibazarPageBody) == -1)
                    return "Nelze získat zbývající aktualizace na mimibazaru.";
            }
        } catch (Exception e) {
            Log.e(TAG, getExAsStr(e));
            return "Při testu externích chyb nastala neočekávaná chyba.";
        }

        return null;
    }
}
