package com.gmail.matejpesl1.mimi.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.fragments.TimePickerFragment;
import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "AppPrefs";
    private Switch updateSwitch;
    private TextView stateDescriptionText;
    private TextView todayUpdatedValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Elements initialization
        updateSwitch = findViewById(R.id.updateSwitch);
        stateDescriptionText = findViewById(R.id.updatingStateDescription);
        todayUpdatedValue = findViewById(R.id.todayUpdatedValue);

        updateSwitch.setOnCheckedChangeListener(this::onSwitch);

        updateView();

        if (!Utils.hasBatteryException(this))
            Utils.requestBatteryException(this);

        RootUtils.askForRoot();

    }

    public void handleUpdateNowButtClick(View v) {
        AsyncTask.execute(() -> Updater.update(this));
    }

    public void openSettings(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void updateView() {
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

        new Thread(() -> {
            int remaining = Updater.tryGetRemainingUpdates();
            int max = Updater.tryGetMaxUpdates();

            String maxStr = (max == -1 ? "-" : max+"");
            String remainingStr = (remaining == -1 || max == -1 ? "-" : (max - remaining)+"");

            runOnUiThread(() -> todayUpdatedValue.setText(String.format("%s/%s", remainingStr, maxStr)));
        }).start();
    }

    public static String dateToCzech(Date time) {
        return new SimpleDateFormat("dd. MM. yyyy H:mm (EEEE)", new Locale("cs", "CZ")).format(time);
    }

    public void onSwitch(CompoundButton buttonView, boolean isChecked) {
        UpdateServiceAlarmManager.changeRepeatingAlarm(this, updateSwitch.isChecked());
        updateView();
    }
}