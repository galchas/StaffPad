package com.example.staffpad;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;

import com.example.staffpad.utils.SharedPreferencesHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsDialogFragment extends DialogFragment {

    public static SettingsDialogFragment newInstance() {
        return new SettingsDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.activity_settings, null);

        SharedPreferencesHelper preferencesHelper = new SharedPreferencesHelper(requireContext());

        RadioGroup themeGroup = view.findViewById(R.id.radioGroupTheme);
        RadioButton rbSystem = view.findViewById(R.id.radioSystem);
        RadioButton rbLight = view.findViewById(R.id.radioLight);
        RadioButton rbDark = view.findViewById(R.id.radioDark);

        int current = preferencesHelper.getThemeMode();
        if (current == AppCompatDelegate.MODE_NIGHT_NO) {
            rbLight.setChecked(true);
        } else if (current == AppCompatDelegate.MODE_NIGHT_YES) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.radioLight) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.radioDark) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            preferencesHelper.setThemeMode(mode);
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> dismiss())
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                .create();
    }
}
