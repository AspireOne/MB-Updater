package com.gmail.matejpesl1.mimi.activities;

import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.gmail.matejpesl1.mimi.R;
import com.gmail.matejpesl1.mimi.UpdateServiceAlarmManager;
import com.gmail.matejpesl1.mimi.Updater;
import com.gmail.matejpesl1.mimi.fragments.TimePickerFragment;
import com.gmail.matejpesl1.mimi.services.UpdateService;
import com.gmail.matejpesl1.mimi.utils.RootUtils;
import com.gmail.matejpesl1.mimi.utils.Utils;

import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREF_UPDATED_PAGES_SPINNER_ITEM_POS = "Updated Pages Spinner Item Pos";
    private EditText updateTimeBox;
    private Spinner updatedPagesSpinner;
    private Switch allowChangeWifiSwitch;
    private Switch allowChangeDataSwitch;
    private TextView rootAllowed;
    private TextView backgroundRunAllowed;
    private Button allowRootButt;
    private Button allowBackgroundRunButt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Scene initialization
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        // Element initialization
        updateTimeBox = findViewById(R.id.updateTimeBox);
        updatedPagesSpinner = findViewById(R.id.updatedPagesSpinner);
        allowChangeWifiSwitch = findViewById(R.id.allowChangeWifiSwitch);
        allowChangeDataSwitch = findViewById(R.id.allowChangeDataSwitch);
        backgroundRunAllowed = findViewById(R.id.backgroundRunAllowedValue);
        rootAllowed = findViewById(R.id.rootAllowedValue);
        allowRootButt = findViewById(R.id.allowRootButt);
        allowBackgroundRunButt = findViewById(R.id.allowBackgroundRunButt);

        // Initial data initialization (of data that are not updated in UpdateView method.
        int posFromPrefs = Integer.parseInt(Utils.getPref(this, PREF_UPDATED_PAGES_SPINNER_ITEM_POS, "4"));
        updatedPagesSpinner.setSelection(posFromPrefs, true);

        // Listeners
        updateTimeBox.setOnClickListener(this::onTimePickerClick);

        allowBackgroundRunButt.setOnClickListener((View v) -> Utils.requestBatteryException(this));
        allowRootButt.setOnClickListener((View v) -> RootUtils.askForRoot());

        allowChangeDataSwitch.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked)
                        -> UpdateService.setAllowDataChange(this, isChecked));

        allowChangeWifiSwitch.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked)
                        -> UpdateService.setAllowWifiChange(this, isChecked));

        updatedPagesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                String[] pageValues = getResources().getStringArray(R.array.updated_pages_values);
                int pagesAmount = Integer.parseInt(pageValues[pos]);

                Updater.changeAmountOfUpdatedPages(SettingsActivity.this, pagesAmount);
                Utils.writePref(SettingsActivity.this, PREF_UPDATED_PAGES_SPINNER_ITEM_POS, pos+"");
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });

        // Logic
        updateView();
    }

    private void onTimePickerClick(View v) {
        TimePickerFragment picker = new TimePickerFragment(
                this::handleTimePicked,
                UpdateServiceAlarmManager.getCurrUpdateCalendar(this));
        picker.show(getSupportFragmentManager(), "timePicker");
    }

    private void handleTimePicked(int hour, int minute) {
        UpdateServiceAlarmManager.changeUpdateTime(this, minute, hour);
        if (UpdateServiceAlarmManager.isRegistered(this)) {
            UpdateServiceAlarmManager.changeRepeatingAlarm(this, false);
            UpdateServiceAlarmManager.changeRepeatingAlarm(this, true);
        }
        updateView();
    }

    private void updateView() {
        updateTimeBox.setText(dateToDigitalTime(UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime()));
        allowChangeDataSwitch.setChecked(UpdateService.getAllowDataChange(this));
        allowChangeWifiSwitch.setChecked(UpdateService.getAllowWifiChange(this));
        if (RootUtils.isRootAvailable()) {
            rootAllowed.setText("ano");
            allowRootButt.setVisibility(View.INVISIBLE);
        } else {
            rootAllowed.setText("ne");
            allowRootButt.setVisibility(View.VISIBLE);
        }

        if (Utils.hasBatteryException(this)) {
            backgroundRunAllowed.setText("ano");
            allowBackgroundRunButt.setVisibility(View.INVISIBLE);
        } else {
            backgroundRunAllowed.setText("ne");
            allowBackgroundRunButt.setVisibility(View.VISIBLE);
        }
    }

    public static String dateToDigitalTime(Date time) {
        return new SimpleDateFormat("H:mm", new Locale("cs", "CZ")).format(time);
    }
}