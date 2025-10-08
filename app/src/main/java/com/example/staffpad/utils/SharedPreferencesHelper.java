package com.example.staffpad.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Helper class to manage app preferences including last opened sheet and page
 */
public class SharedPreferencesHelper {
    private static final String TAG = "PreferencesHelper";
    private static final String PREFS_NAME = "StaffPadPrefs";

    // Keys for preferences
    private static final String KEY_LAST_SHEET_ID = "last_sheet_id";
    private static final String KEY_LAST_PAGE_NUMBER = "last_page_number";

    private final SharedPreferences preferences;

    public SharedPreferencesHelper(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save the last viewed sheet ID and page number
     *
     * @param sheetId ID of the sheet being viewed
     * @param pageNumber Current page number being viewed
     */
    public void saveLastViewedPage(long sheetId, int pageNumber) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(KEY_LAST_SHEET_ID, sheetId);
        editor.putInt(KEY_LAST_PAGE_NUMBER, pageNumber);
        boolean success = editor.commit(); // Using commit() instead of apply() to ensure immediate write

        Log.d(TAG, "Saved last viewed page: Sheet ID=" + sheetId +
                ", Page=" + pageNumber + ", Success=" + success);
    }

    /**
     * Get the last viewed sheet ID
     *
     * @return Last viewed sheet ID or -1 if not found
     */
    public long getLastViewedSheetId() {
        return preferences.getLong(KEY_LAST_SHEET_ID, -1);
    }

    /**
     * Get the last viewed page number
     *
     * @return Last viewed page number or 0 if not found
     */
    public int getLastViewedPageNumber() {
        return preferences.getInt(KEY_LAST_PAGE_NUMBER, 0);
    }

    /**
     * Check if we have saved last view information
     *
     * @return true if last view information exists
     */
    public boolean hasLastViewInformation() {
        return getLastViewedSheetId() != -1;
    }

    /**
     * Clear all saved preferences
     */
    public void clearAllPreferences() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Cleared all preferences");
    }
}