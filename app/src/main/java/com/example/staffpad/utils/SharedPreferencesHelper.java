package com.example.staffpad.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Helper class to manage app preferences including last opened sheet and page + theme mode
 */
public class SharedPreferencesHelper {
    private static final String TAG = "PreferencesHelper";
    private static final String PREFS_NAME = "StaffPadPrefs";

    // Keys for preferences
    private static final String KEY_LAST_SHEET_ID = "last_sheet_id";
    private static final String KEY_LAST_PAGE_NUMBER = "last_page_number";
    private static final String KEY_THEME_MODE = "theme_mode"; // stores AppCompatDelegate mode int

    private final SharedPreferences preferences;

    public SharedPreferencesHelper(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save the last viewed sheet ID and page number
     */
    public void saveLastViewedPage(long sheetId, int pageNumber) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(KEY_LAST_SHEET_ID, sheetId);
        editor.putInt(KEY_LAST_PAGE_NUMBER, pageNumber);
        boolean success = editor.commit(); // Using commit() instead of apply() to ensure immediate write

        Log.d(TAG, "Saved last viewed page: Sheet ID=" + sheetId +
                ", Page=" + pageNumber + ", Success=" + success);
    }

    /** Get the last viewed sheet ID */
    public long getLastViewedSheetId() {
        return preferences.getLong(KEY_LAST_SHEET_ID, -1);
    }

    /** Get the last viewed page number */
    public int getLastViewedPageNumber() {
        return preferences.getInt(KEY_LAST_PAGE_NUMBER, 0);
    }

    /** Check if we have saved last view information */
    public boolean hasLastViewInformation() {
        return getLastViewedSheetId() != -1;
    }

    /** Clear all saved preferences */
    public void clearAllPreferences() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Cleared all preferences");
    }

    // ---------------- THEME MODE ----------------
    /**
     * Persist the chosen theme mode.
     * @param mode One of AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, MODE_NIGHT_NO, MODE_NIGHT_YES
     */
    public void setThemeMode(int mode) {
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    /**
     * Returns the saved theme mode, defaulting to MODE_NIGHT_FOLLOW_SYSTEM.
     */
    public int getThemeMode() {
        return preferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * Applies the saved theme mode to the app.
     */
    public void applySavedTheme() {
        int mode = getThemeMode();
        AppCompatDelegate.setDefaultNightMode(mode);
        Log.d(TAG, "Applied theme mode: " + mode);
    }
}