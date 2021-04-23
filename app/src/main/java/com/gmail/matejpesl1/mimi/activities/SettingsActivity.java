package com.gmail.matejpesl1.mimi.activities;

import android.content.Context;
import android.icu.util.Calendar;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.Date;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Nastavení");
        }
    }

    // Otherwise the "back" button in actionBar does nothing.
    @Override public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private Context context;
        private Preference updateTimePref;
        private Preference allowDataChangePref;
        private Preference rootPermissionPref;
        private Preference batteryExceptionPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            // Initialize.
            updateTimePref = findPreference(getString(R.string.setting_update_time_key));
            rootPermissionPref = findPreference(getString(R.string.setting_root_permission_key));
            allowDataChangePref = findPreference(getString(R.string.setting_allow_data_change_key));
            batteryExceptionPref = findPreference(getString(R.string.setting_battery_exception_key));

            // Update state.
            updateUpdateTimeSummary();
            checkRootAndUpdateSettingsAsync();

            if (Utils.hasBatteryException(context))
                batteryExceptionPref.setSummary("Povoleno");

            // Set listeners.
            setOnClickListeners();
        }

        private void setOnClickListeners() {
            updateTimePref.setOnPreferenceClickListener(preference -> {
                MaterialTimePicker picker = new MaterialTimePicker.Builder()
                        .setHour(UpdateServiceAlarmManager.getCurrUpdateCalendar(context).get(Calendar.HOUR))
                        .setMinute(UpdateServiceAlarmManager.getCurrUpdateCalendar(context).get(Calendar.MINUTE))
                        .setTitleText("Vyberte čas aktualizací")
                        .build();

                picker.addOnPositiveButtonClickListener((v) -> {
                    handleTimePicked(picker.getHour(), picker.getMinute());
                    picker.dismiss();
                });

                picker.show(getParentFragmentManager(), "timePicker");
                return true;
            });

            rootPermissionPref.setOnPreferenceClickListener(preference -> {
                checkRootAndUpdateSettingsAsync();
                return true;
            });

            batteryExceptionPref.setOnPreferenceClickListener(preference -> {
                if (!Utils.hasBatteryException(context))
                    Utils.requestBatteryException(context);
                return true;
            });
        }

        private void checkRootAndUpdateSettingsAsync() {
            new Thread(() -> {
                // Checking it also requests it at the same time.
                if (RootUtils.isRootAvailable()) {
                    getActivity().runOnUiThread(() -> {
                        rootPermissionPref.setSummary("Povoleno");
                        allowDataChangePref.setEnabled(true);
                    });
                }
            }).start();
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            this.context = context;
        }

        private void handleTimePicked(int hour, int minute) {
            UpdateServiceAlarmManager.changeUpdateTime(context, minute, hour);
            updateUpdateTimeSummary();
        }

        private void updateUpdateTimeSummary() {
            Date currUpdateDate = UpdateServiceAlarmManager.getCurrUpdateCalendar(context).getTime();
            updateTimePref.setSummary(Utils.dateToDigitalTime(currUpdateDate));
        }
    }
}