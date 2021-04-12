package com.gmail.matejpesl1.mimi.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static final String GLOBAL_PREFS_NAME = "AppPrefs";
    private static final Requester requester = new Requester(0);
    private static boolean allowancesRequested = false;

    private static MimibazarRequester mimibazarRequester = null;
    private Switch updateSwitch;
    private TextView stateDescriptionText;
    private TextView todayUpdatedValue;
    private TextView appUpdateAvailableTxt;
    private TextView badCredentialsWarning;
    private Button updateAppButt;
    private Button updateNowButt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Elements initialization
        updateSwitch = findViewById(R.id.updateSwitch);
        stateDescriptionText = findViewById(R.id.updatingStateDescription);
        todayUpdatedValue = findViewById(R.id.todayUpdatedValue);
        appUpdateAvailableTxt = findViewById(R.id.updateAvailableTxt);
        updateAppButt = findViewById(R.id.updateAppButt);
        badCredentialsWarning = findViewById(R.id.badCredentialsWarning);
        updateNowButt = findViewById(R.id.updateNowButt);

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

        updateAppButt.setOnClickListener((view) -> {
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
        if (!allowancesRequested) {
            allowancesRequested = true;
            new Thread(() -> {
                try { Thread.sleep(2000); }
                catch (InterruptedException e) { Log.i(TAG, Utils.getExceptionAsString(e)); }

                if (!Utils.hasBatteryException(this))
                    Utils.requestBatteryException(this);

                try { Thread.sleep(2000); }
                catch (InterruptedException e) { Log.i(TAG, Utils.getExceptionAsString(e)); }

                RootUtils.askForRoot();
            }).start();
        }

        updateView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateView();
    }

    public void openSettings(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
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

            if (UpdateServiceAlarmManager.isRegistered(this) && !accValid) {
                UpdateServiceAlarmManager.changeRepeatingAlarm(this, false);
                runOnUiThread(() -> updateAlarm());
            }

            // Update remaining updates. This comes last, because it takes longer to complete.
            updateRemaining();
        }).start();

        // Update alarm manager state & description.
        updateAlarm();
    }

    private boolean updateAccountAndRelated() {
        final String username = Updater.getUsername(this);
        final String pass = Updater.getPassword(this);
        final boolean hasUsername = !Utils.isEmptyOrNull(username);
        final boolean hasPass = !Utils.isEmptyOrNull(pass);

        final String error;

        if (!hasUsername && !hasPass)
            error = "Chybí přihlašovací údaje. Je třeba je v nastavení vyplnit.";
        else if (!hasUsername)
            error = "Chybí uživatelské jméno. Je třeba jej v nastavení vyplnit.";
        else if (!hasPass)
            error = "Chybí uživatelské heslo. Je třeba jej v nastavení vyplnit.";
        else {
            boolean err = false;
            try {
                mimibazarRequester = new MimibazarRequester(requester, username, pass);
            } catch (MimibazarRequester.CouldNotGetAccIdException e) {
                err = true;
                Log.e(TAG, "Could not create mimibazarRequester because credentials are not valid. E: " + Utils.getExceptionAsString(e));
            }

            error = err ? "Uživatelské údaje nejsou správné. Je třeba je v nastavení změnit." : "";
        }

        final boolean allValid = error.equals("");

        Log.i(TAG, String.format("Account info valid: %s | error (if any): ", allValid, error));

        if (!allValid)
            mimibazarRequester = null;

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
            String nextUpdateDateStr = Utils.dateToCzech(nextUpdateDate);

            updateSwitch.setChecked(true);
            stateDescriptionText.setText(String.format("%s %s %s",
                    getResources().getString(R.string.updating_on_description),
                    getResources().getString(R.string.next_update_in),
                    nextUpdateDateStr));
        } else {
            updateSwitch.setChecked(false);
            stateDescriptionText.setText(R.string.updating_off_description);
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