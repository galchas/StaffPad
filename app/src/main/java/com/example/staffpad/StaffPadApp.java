package com.example.staffpad;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class StaffPadApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply Material You dynamic colors if available (system-based light/dark + Material3 palettes)
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
