package com.example.staffpad;

import android.app.Application;

import com.example.staffpad.utils.SharedPreferencesHelper;
import com.google.android.material.color.DynamicColors;

public class StaffPadApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply saved theme mode before any Activity is created
        new SharedPreferencesHelper(this).applySavedTheme();
        // Apply Material You dynamic colors if available (system-based light/dark + Material3 palettes)
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
