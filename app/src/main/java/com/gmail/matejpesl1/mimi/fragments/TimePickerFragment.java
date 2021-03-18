package com.gmail.matejpesl1.mimi.fragments;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.icu.util.Calendar;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TimePicker;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {
    private final ITimePickerClickHandler clickHandler;
    private final Calendar defaultTime;

    public TimePickerFragment(ITimePickerClickHandler clickHandler, Calendar defaultTime) {
        super();
        this.clickHandler = clickHandler;
        this.defaultTime = defaultTime;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int hour = defaultTime.get(Calendar.HOUR_OF_DAY);
        int minute = defaultTime.get(Calendar.MINUTE);

        return new TimePickerDialog(getActivity(), this, hour, minute,
                DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
        Log.d("", hour+"");
        clickHandler.handleClick(hour, minute);
    }

}