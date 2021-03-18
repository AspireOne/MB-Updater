package com.gmail.matejpesl1.mimi;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.gmail.matejpesl1.mimi.fragments.TimePickerFragment;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Locale;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "AppPrefs";
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch updateSwitch;
    private TextView stateDescriptionText;
    private EditText updateTimeBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateSwitch = (Switch)findViewById(R.id.updateSwitch);
        stateDescriptionText = (TextView)findViewById(R.id.updatingStateDescription);
        updateTimeBox = (EditText)findViewById(R.id.updateTimeBox);

        updateSwitch.setOnCheckedChangeListener(this::onSwitch);
        updateTimeBox.setOnClickListener(this::onTimePickerClick);

        updateView();
        // TODO: Disable this if you can.
        requestBatteryException();

        RootUtils.askForRoot();
    }

    private void requestBatteryException() {
        String packageName = getPackageName();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }

    private void onTimePickerClick(View v) {
        TimePickerFragment picker = new TimePickerFragment(this::handleTimePicked, Calendar.getInstance());
        picker.show(getSupportFragmentManager(), "timePicker");
    }

    private void handleTimePicked(int hour, int minute) {
        Log.d("", String.format("hour: %s, minute: %s", hour, minute));
        UpdateServiceAlarmManager.changeUpdateTime(this, minute, hour);
        updateView();
    }

    private void updateView() {
        updateTimeBox.setText(dateToDigitalTime(UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime()));
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

    public static String dateToDigitalTime(Date time) {
        return new SimpleDateFormat("HH:mm", new Locale("cs", "CZ")).format(time);
    }

    public static String dateToCzech(Date time) {
        return new SimpleDateFormat("dd. MM. yyyy H:mm (EEEE)", new Locale("cs", "CZ")).format(time);
    }

    public void onSwitch(CompoundButton buttonView, boolean isChecked) {
        UpdateServiceAlarmManager.changeRepeatingAlarm(this, updateSwitch.isChecked());
        updateView();
    }
}