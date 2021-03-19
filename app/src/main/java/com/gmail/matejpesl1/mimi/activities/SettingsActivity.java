package com.gmail.matejpesl1.mimi.activities;

import android.icu.util.Calendar;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.fragments.TimePickerFragment;

public class SettingsActivity extends AppCompatActivity {
    private EditText updateTimeBox;
    private Spinner updatedPagesSpinner;
    private Switch allowChangeWifiSwitch;
    private Switch allowChangeDataSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        updateTimeBox = (EditText)findViewById(R.id.updateTimeBox);
        updatedPagesSpinner = (Spinner)findViewById(R.id.updatedPagesSpinner);
        allowChangeWifiSwitch = (Switch)findViewById(R.id.allowChangeWifiSwitch);
        allowChangeDataSwitch = (Switch)findViewById(R.id.allowChangeDataSwitch);


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
        //updateTimeBox.setText(dateToDigitalTime(UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime()));
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}