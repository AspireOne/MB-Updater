package com.gmail.matejpesl1.mimi.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.gmail.matejpesl1.mimi.AppUpdateManager;
import com.gmail.matejpesl1.mimi.MimibazarRequester;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.Requester;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.InternetUtils;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.Date;

import static com.gmail.matejpesl1.mimi.utils.Utils.dateToCzech;
import static com.gmail.matejpesl1.mimi.utils.Utils.getExAsStr;
import static com.gmail.matejpesl1.mimi.utils.Utils.isEmptyOrNull;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static final String PREFS_NAME = "com.gmail.matejpesl1.mimi_preferences";
    private static final Requester requester = new Requester(0);

    private static MimibazarRequester mimibazarRequester = null;
    private Switch updateSwitch;
    private ImageView settingsIcon;
    private TextView stateDescriptionText;
    private TextView todayUpdatedValue;
    private TextView appUpdateAvailableTxt;
    private TextView badCredentialsWarning;
    private Button updateAppButt;
    private Button updateNowButt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new Thread(() -> new Updater(this).execute()).start();
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
            // TODO: Solve this without root.
            Log.i(TAG, "Update app button clicked.");
            boolean rootAvailable = RootUtils.isRootAvailable();
            new Thread(() -> {
                if (rootAvailable)
                    AppUpdateManager.installDirectlyWithRoot(this);
                else
                    AppUpdateManager.requestInstall(this);
            }).start();

            if (rootAvailable) {
                Intent startMain = new Intent(Intent.ACTION_MAIN);
                startMain.addCategory(Intent.CATEGORY_HOME);
                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startMain);
            }
        });

        // Logic
        PreferenceManager.setDefaultValues(this, PREFS_NAME, MODE_PRIVATE, R.xml.root_preferences, false);
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
            updateRemaining();
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

    private void updateRemaining() {
        int remaining = -1;
        int max = -1;
        if (mimibazarRequester != null) {
            String pageBody = mimibazarRequester.getPageBodyOrNull(1, true);
            remaining = mimibazarRequester.tryGetRemainingUpdates(pageBody);
            max = mimibazarRequester.tryGetMaxUpdates(pageBody);
        }

        String maxStr = (max == -1 ? "-" : max + "");
        String remainingStr = (max == -1 || remaining == -1 ? "-" : (max - remaining) + "");

        runOnUiThread(() -> todayUpdatedValue.setText(String.format("%s/%s", remainingStr, maxStr)));
    }
}