package com.gmail.matejpesl1.mimi.activities;

import android.content.Intent;
import android.icu.text.SimpleDateFormat;
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
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String GLOBAL_PREFS_NAME = "AppPrefs";
    private static final String TAG = "MainActivity";
    private Requester requester;
    private MimibazarRequester mimibazarRequester = null;
    private Switch updateSwitch;
    private TextView stateDescriptionText;
    private TextView todayUpdatedValue;
    private TextView appUpdateAvailableTxt;
    private TextView badCredentialsWarning;
    private Button updateAppButt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fields initialization
        requester = new Requester(0);

        // Elements initialization
        updateSwitch = findViewById(R.id.updateSwitch);
        stateDescriptionText = findViewById(R.id.updatingStateDescription);
        todayUpdatedValue = findViewById(R.id.todayUpdatedValue);
        appUpdateAvailableTxt = findViewById(R.id.updateAvailableTxt);
        updateAppButt = findViewById(R.id.updateAppButt);
        badCredentialsWarning = findViewById(R.id.badCredentialsWarning);

        // Listeners
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UpdateServiceAlarmManager.changeRepeatingAlarm(MainActivity.this, updateSwitch.isChecked());
            updateAlarm();
        });

        updateAppButt.setOnClickListener((view) -> {
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
        new Thread(() -> {
            RootUtils.askForRoot();

            if (!Utils.hasBatteryException(this))
                Utils.requestBatteryException(this);
        }).start();

        updateView();
    }

    public void openSettings(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateView();
    }

    private void updateView() {
        // Update app update visibility.
        new Thread(() -> updateAppUpdateVisibility()).start();

        // Updates that require mimibazarRequester to be initialized.
        new Thread(() -> {
            try {
                mimibazarRequester = new MimibazarRequester(requester,
                        Updater.getUsername(this),
                        Updater.getPassword(this));
            } catch (MimibazarRequester.CouldNotGetAccIdException e) {
                Log.e(TAG, Utils.getExceptionAsString(e));
            }

            // Update remaining updates.
            new Thread(() -> updateRemaining()).start();
            // Update credentials.
            new Thread(() -> updateCredentialsWarning()).start();
        }).start();

        // Update alarm manager state & description.
        updateAlarm();
    }

    private void updateCredentialsWarning() {
        String username = Updater.getUsername(this);
        String password = Updater.getPassword(this);

        if (Utils.isEmptyOrNull(username) || Utils.isEmptyOrNull(password) || mimibazarRequester.tryGetUserId() == -1) {
            badCredentialsWarning.setVisibility(View.VISIBLE);
            if (updateSwitch.isEnabled()) {
                updateSwitch.setEnabled(false);
                UpdateServiceAlarmManager.changeRepeatingAlarm(this, false);
                updateAlarm();
            }
        } else {
            badCredentialsWarning.setVisibility(View.INVISIBLE);
            if (!updateSwitch.isEnabled()) {
                updateSwitch.setEnabled(true);
                UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);
                updateAlarm();
            }
        }
    }

    private void updateAlarm() {
        if (UpdateServiceAlarmManager.isRegistered(this)) {
            updateSwitch.setChecked(true);
            Date nextUpdateDate = UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime();
            String nextUpdateDateStr = Utils.dateToCzech(nextUpdateDate);

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
        boolean updateAvailable = AppUpdateManager.isUpdateAvailable();
        int visibility = updateAvailable ? View.VISIBLE : View.INVISIBLE;

        runOnUiThread(() -> {
            updateAppButt.setVisibility(visibility);
            appUpdateAvailableTxt.setVisibility(visibility);
        });

        if (updateAvailable && !AppUpdateManager.isDownloadedApkLatest(this)) {
            Log.d("MainActivity", "downloading apk because the one already downloaded" +
                    "(if any) is not latest.");
            AppUpdateManager.downloadApk(this);
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

        String maxStr = (max == -1 ? "-" : max+"");
        String remainingStr = (max == -1 || remaining == -1 ? "-" : (max - remaining)+"");

        runOnUiThread(() -> todayUpdatedValue.setText(String.format("%s/%s", remainingStr, maxStr)));
    }
}