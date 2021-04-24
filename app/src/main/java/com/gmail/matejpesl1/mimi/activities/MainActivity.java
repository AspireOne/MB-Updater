package com.gmail.matejpesl1.mimi.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import com.gmail.matejpesl1.mimi.AppUpdateManager;
import com.gmail.matejpesl1.mimi.MimibazarRequester;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.Requester;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;

import java.util.Date;

import static com.gmail.matejpesl1.mimi.utils.Utils.dateToCzech;
import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;
import static com.gmail.matejpesl1.mimi.utils.Utils.isEmptyOrNull;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static final String PREFS_NAME = "com.gmail.matejpesl1.mimi_preferences";
    private static final Requester requester = new Requester(0);

    private static MimibazarRequester mimibazarRequester = null;
    private SwitchCompat updateSwitch;
    private ShapeableImageView settingsIcon;
    private MaterialTextView stateDescriptionText;
    private MaterialTextView todayUpdatedValue;
    private MaterialTextView appUpdateAvailableTxt;
    private MaterialTextView badCredentialsWarning;
    private MaterialButton updateAppButt;
    private MaterialButton updateNowButt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Elements initialization
        updateSwitch = findViewById(R.id.switch_autoUpdate);
        stateDescriptionText = findViewById(R.id.textView_updatingStateDescription);
        todayUpdatedValue = findViewById(R.id.textView_todayUpdatedPlaceholder);
        appUpdateAvailableTxt = findViewById(R.id.textView_appUpdateAvailable);
        updateAppButt = findViewById(R.id.button_updateApp);
        badCredentialsWarning = findViewById(R.id.textView_badCredentialsPlaceholder);
        updateNowButt = findViewById(R.id.button_updateNow);
        settingsIcon = findViewById(R.id.imageView_settings);

        // Listeners
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.i(TAG, "Update switch changed to " + updateSwitch.isChecked());
            UpdateServiceAlarmManager.changeRepeatingAlarm(MainActivity.this, updateSwitch.isChecked());
            updateAlarm();
        });

        updateNowButt.setOnClickListener((view) -> {
            Log.i(TAG, "Update now button clicked.");
            UpdateService.startUpdateImmediately(this);
        });

        settingsIcon.setOnClickListener((view) -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        updateAppButt.setOnClickListener((view) -> {
            Log.i(TAG, "Update app button clicked.");
            new Thread(() -> AppUpdateManager.requestInstall(this)).start();
        });

        // Logic
        PreferenceManager.setDefaultValues(this, PREFS_NAME, MODE_PRIVATE, R.xml.root_preferences, false);
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES)
            settingsIcon.setColorFilter(Color.rgb(210, 200, 200));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateView();
    }

    private void updateView() {
        // Updates that require internet.
        new Thread(() -> {
            if (!InternetUtils.isConnectionAvailable()) {
                Log.i(TAG, "Internet connection not available.");
                return;
            }

            // Update app update visibility.
            updateAppUpdateVisibility();

            // Update credentials and initialize mimibazar requester.
            boolean accValid = updateAccountAndRelated();

            if (!accValid) {
                if (UpdateServiceAlarmManager.isRegistered(this)) {
                    UpdateServiceAlarmManager.changeRepeatingAlarm(this, false);
                    runOnUiThread(this::updateAlarm);
                }
                return;
            }

            // Update remaining updates. This comes last, because it takes longer to complete.
            Pair<Integer, Integer> state = mimibazarRequester.getUpdatesState();

            runOnUiThread(() -> updateNowButt.setEnabled(state.first.intValue() > 0));
            updateRemaining(state.first, state.second);
        }).start();

        // Update alarm manager state & description.
        updateAlarm();
    }

    private boolean updateAccountAndRelated() {
        final String username = Utils.getPref(this, R.string.setting_username_key, "");
        final String pass = Utils.getPref(this, R.string.setting_password_key, "");
        final boolean hasUsername = !isEmptyOrNull(username);
        final boolean hasPass = !isEmptyOrNull(pass);

        final String error;

        if (!hasUsername && !hasPass)
            error = "Chybí přihlašovací údaje. Je třeba je v nastavení vyplnit.";
        else if (!hasUsername)
            error = "Chybí uživatelské jméno. Je třeba jej v nastavení vyplnit.";
        else if (!hasPass)
            error = "Chybí uživatelské heslo. Je třeba jej v nastavení vyplnit.";
        else {
            boolean initErr = false;
            try {
                mimibazarRequester = new MimibazarRequester(requester, username, pass);
            } catch (MimibazarRequester.CouldNotGetAccIdException e) {
                initErr = true;
                Log.e(TAG, "Could not create mimibazarRequester because credentials are not valid. E: " + getExAsStr(e));
            }

            error = initErr ? "Uživatelské údaje nejsou správné. Je třeba je v nastavení změnit." : "";
        }

        final boolean allValid = error.equals("");

        if (!allValid)
            mimibazarRequester = null;

        Log.i(TAG, String.format("Account info valid: %s | error (if any): %s", allValid, error));

        boolean needsUpdate =
                (allValid && !updateSwitch.isEnabled()) ||
                        (!allValid && updateSwitch.isEnabled());

        if (!allValid)
            Log.i(TAG, error);

        runOnUiThread(() -> {
            if (needsUpdate) {
                badCredentialsWarning.setText(error);
                badCredentialsWarning.setVisibility(allValid ? View.INVISIBLE : View.VISIBLE);
                updateSwitch.setEnabled(allValid);
            }
        });

        return allValid;
    }

    private void updateAlarm() {
        if (UpdateServiceAlarmManager.isRegistered(this)) {
            Date nextUpdateDate = UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime();
            String nextUpdateDateStr = dateToCzech(nextUpdateDate);

            updateSwitch.setChecked(true);
            stateDescriptionText.setText(String.format("%s %s %s",
                    getResources().getString(R.string.activity_main_updating_on_desc),
                    getResources().getString(R.string.activity_main_next_update_in),
                    nextUpdateDateStr));
        } else {
            updateSwitch.setChecked(false);
            stateDescriptionText.setText(R.string.activity_main_updating_off_desc);
        }
    }

    private void updateAppUpdateVisibility() {
        boolean updateAvailable = AppUpdateManager.isUpdateAvailable(this);
        Log.i(TAG, "App update available: " + updateAvailable);
        final int visibility = updateAvailable ? View.VISIBLE : View.INVISIBLE;

        if (updateAvailable) {
            runOnUiThread(() -> {
                updateAppButt.setVisibility(visibility);
                appUpdateAvailableTxt.setVisibility(visibility);
            });

            if (!AppUpdateManager.isDownloadedApkLatest(this)) {
                runOnUiThread(() -> updateAppButt.setEnabled(false));
                AppUpdateManager.downloadApkAsync(this, (downloadState) -> runOnUiThread(() -> updateAppButt.setEnabled(true)));
                Log.i("MainActivity", "Downloading update apk.");
            }
        }
    }

    /* Returns true if all updates were already used. */
    private void updateRemaining(int remaining, int max) {
        String maxStr = (max == -1 ? "-" : max + "");
        String remainingStr = (max == -1 || remaining == -1 ? "-" : (max - remaining) + "");

        runOnUiThread(() -> todayUpdatedValue.setText(String.format("%s/%s", remainingStr, maxStr)));
    }
}