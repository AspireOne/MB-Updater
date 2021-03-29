package com.gmail.matejpesl1.mimi.activities;

import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private static final String TAG = "SettingsActivity";
    private static final String PREF_UPDATED_PAGES_SPINNER_ITEM_POS = "Updated Pages Spinner Item Pos";
    private EditText timePicker;
    private Spinner updatedPagesSpinner;
    private Switch allowChangeWifiSwitch;
    private Switch allowChangeDataSwitch;
    private Switch notifyAboutSuccesfullUpdateSwitch;
    private TextView rootAllowedValue;
    private TextView backgroundRunAllowedValue;
    private EditText passwordBox;
    private EditText usernameBox;
    private Button allowRootButt;
    private Button allowBackgroundRunButt;

    private boolean rootAllowedCached = false;
    private boolean backgroundRunAllowedCached = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Scene initialization
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        // Element initialization
        notifyAboutSuccesfullUpdateSwitch = findViewById(R.id.notifyAboutSuccesfullUpdateSwitch);
        backgroundRunAllowedValue = findViewById(R.id.backgroundRunAllowedValue);
        allowBackgroundRunButt = findViewById(R.id.allowBackgroundRunButt);
        allowChangeWifiSwitch = findViewById(R.id.allowChangeWifiSwitch);
        allowChangeDataSwitch = findViewById(R.id.allowChangeDataSwitch);
        updatedPagesSpinner = findViewById(R.id.updatedPagesSpinner);
        rootAllowedValue = findViewById(R.id.rootAllowedValue);
        allowRootButt = findViewById(R.id.allowRootButt);
        passwordBox = findViewById(R.id.passwordTextbox);
        usernameBox = findViewById(R.id.usernameTextbox);
        timePicker = findViewById(R.id.updateTimeBox);

        // Initial data initialization (of data that are not updated in UpdateView method).
        int posFromPrefs = Integer.parseInt(Utils.getPref(this, PREF_UPDATED_PAGES_SPINNER_ITEM_POS, "4"));
        updatedPagesSpinner.setSelection(posFromPrefs, true);
        updateCredentials();

        // Listeners.
        timePicker.setOnClickListener(this::onTimePickerClick);

        // Root & Battery allowance buttons.
        allowBackgroundRunButt.setOnClickListener((View v) -> Utils.requestBatteryException(this));
        allowRootButt.setOnClickListener((View v) -> RootUtils.askForRoot());

        // Internet switches.
        allowChangeDataSwitch.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked)
                        -> UpdateService.setAllowDataChange(this, isChecked));

        allowChangeWifiSwitch.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked)
                        -> UpdateService.setAllowWifiChange(this, isChecked));
        // Others.
        notifyAboutSuccesfullUpdateSwitch.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked)
                        -> Updater.setNotifyAboutSuccesfullUpdate(this, isChecked));

        updatedPagesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                String[] pageValues = getResources().getStringArray(R.array.updated_pages_values);
                int pagesAmount = Integer.parseInt(pageValues[pos]);

                Updater.setAmountOfUpdatedPages(SettingsActivity.this, pagesAmount);
                Utils.writePref(SettingsActivity.this, PREF_UPDATED_PAGES_SPINNER_ITEM_POS, pos+"");
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });

        // Logic.
        updateView();
    }

    @Override
    public void onStop() {
        super.onStop();
        Updater.setCredentials(this,
                usernameBox.getText().toString(),
                passwordBox.getText().toString());

        Log.d(TAG, usernameBox.getText().toString());
    }

    @Override
    public void onPause() {
        super.onPause();
        Updater.setCredentials(this,
                usernameBox.getText().toString(),
                passwordBox.getText().toString());

        Log.d(TAG, usernameBox.getText().toString());
    }

    private void onTimePickerClick(View v) {
        TimePickerFragment picker = new TimePickerFragment(
                this::handleTimePicked,
                UpdateServiceAlarmManager.getCurrUpdateCalendar(this));
        picker.show(getSupportFragmentManager(), "timePicker");
    }

    private void handleTimePicked(int hour, int minute) {
        UpdateServiceAlarmManager.changeUpdateTime(this, minute, hour);
        Log.d(TAG, "time picked. hour: " + hour + " | minute: " + minute);
        updateTimePicker();
    }

    private void updateView() {
        // First update root, then internet switches.
        updateRootStatus();
        updateInternetChangeSwitches();
        updateTimePicker();
        updateBackgroundRunStatus();
    }

    private void updateCredentials() {
        passwordBox.setText(Updater.getPassword(this));
        usernameBox.setText(Updater.getUsername(this));
    }

    private void updateTimePicker() {
        Date currUpdateDate = UpdateServiceAlarmManager.getCurrUpdateCalendar(this).getTime();
        timePicker.setText(dateToDigitalTime(currUpdateDate));
    }

    private void updateInternetChangeSwitches() {
        allowChangeDataSwitch.setChecked(UpdateService.getAllowDataChange(this) && rootAllowedCached);
        allowChangeDataSwitch.setEnabled(rootAllowedCached);
        allowChangeWifiSwitch.setChecked(UpdateService.getAllowWifiChange(this));
        notifyAboutSuccesfullUpdateSwitch.setChecked(Updater.getNotifyAboutSuccesfullUpdate(this));
    }

    private void updateBackgroundRunStatus() {
        if (backgroundRunAllowedCached || Utils.hasBatteryException(this)) {
            backgroundRunAllowedValue.setText("ano");
            allowBackgroundRunButt.setVisibility(View.INVISIBLE);
            backgroundRunAllowedCached = true;
        } else {
            backgroundRunAllowedValue.setText("ne");
            allowBackgroundRunButt.setVisibility(View.VISIBLE);
            backgroundRunAllowedCached = false;
        }
    }

    private void updateRootStatus() {
        if (rootAllowedCached || RootUtils.isRootAvailable()) {
            rootAllowedValue.setText("ano");
            allowRootButt.setVisibility(View.INVISIBLE);
            rootAllowedCached = true;
        } else {
            rootAllowedValue.setText("ne");
            allowRootButt.setVisibility(View.VISIBLE);
            rootAllowedCached = false;
        }
    }

    public static String dateToDigitalTime(Date time) {
        return new SimpleDateFormat("H:mm", new Locale("cs", "CZ")).format(time);
    }
}