package com.gmail.matejpesl1.mimi.activities;

import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gmail.matejpesl1.mimi.AppUpdateManager;
import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "AppPrefs";
    private Switch updateSwitch;
    private TextView stateDescriptionText;
    private TextView todayUpdatedValue;
    private TextView appUpdateAvailableTxt;
    private Button updateAppButt;

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

        // Listeners
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UpdateServiceAlarmManager.changeRepeatingAlarm(MainActivity.this, updateSwitch.isChecked());
            MainActivity.this.updateView();
        });

        updateAppButt.setOnClickListener((view) -> {
            new Thread(() -> AppUpdateManager.installDirectlyWithRoot(this)).start();
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
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

    private void updateView() {
        // Update remaining updates.
        new Thread(() -> {
            int remaining = Updater.tryGetRemainingUpdates();
            int max = Updater.tryGetMaxUpdates();

            String maxStr = (max == -1 ? "-" : max+"");
            String remainingStr = (remaining == -1 || max == -1 ? "-" : (max - remaining)+"");

            runOnUiThread(() -> todayUpdatedValue.setText(String.format("%s/%s", remainingStr, maxStr)));
        }).start();

        // Update app update visibility.
        new Thread(() -> {
            boolean updateAvailable = AppUpdateManager.isUpdateAvailable();
            int visibility = updateAvailable ? View.VISIBLE : View.INVISIBLE;

            runOnUiThread(() -> {
                updateAppButt.setVisibility(visibility);
                appUpdateAvailableTxt.setVisibility(visibility);
            });

            // We can invoke the download method multiple times, because if it's already downloaded,
            // it'll just return.
            if (updateAvailable && !AppUpdateManager.isDownloadedApkLatest(this))
                AppUpdateManager.downloadApk(this);
        }).start();

        if (UpdateServiceAlarmManager.isRegistered(this)) {
            updateSwitch.setChecked(true);
            String nextUpdateTime = dateToCzech(UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime());

            stateDescriptionText.setText(String.format("%s %s %s",
                    getResources().getString(R.string.updating_on_text),
                    getResources().getString(R.string.next_update_in),
                    nextUpdateTime));
        } else {
            updateSwitch.setChecked(false);
            stateDescriptionText.setText(R.string.updating_off_text);
        }
    }

    public static String dateToCzech(Date time) {
        return new SimpleDateFormat("dd. MM. yyyy H:mm (EEEE)", new Locale("cs", "CZ")).format(time);
    }
}