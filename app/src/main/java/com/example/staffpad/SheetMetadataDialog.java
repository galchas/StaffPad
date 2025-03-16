package com.example.staffpad;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.data_model.AudioFile;
import com.example.staffpad.data_model.Library;
import com.example.staffpad.data_model.Setlist;
import com.example.staffpad.data_model.SheetMusic;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.viewmodel.SheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SheetMetadataDialog extends BottomSheetDialogFragment {

    // Current sheet
    private SheetMusic sheetMusic;

    // UI Components
    private FrameLayout contentContainer;
    private Button propertiesButton;
    private Button setlistButton;
    private Button audioButton;
    private Button librariesButton;

    // Properties content views
    private TextInputEditText titleInput;
    private TextInputEditText composersInput;
    private TextInputEditText genresInput;
    private TextInputEditText labelsInput;
    private TextInputEditText referencesInput;
    private RatingBar ratingBar;
    private TextView selectedKeyText;
    Dialog dialog;

    // Interface for callbacks
    public interface SheetMetadataListener {
        void onMetadataUpdated(SheetMusic sheetMusic);
    }

    private SheetMetadataListener listener;

    // Audio file picker
    private ActivityResultLauncher<Intent> audioPickerLauncher;

    public static SheetMetadataDialog newInstance(SheetMusic sheetMusic) {
        SheetMetadataDialog dialog = new SheetMetadataDialog();
        Bundle args = new Bundle();
        args.putParcelable("sheet_music", sheetMusic);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get sheet music from arguments
        if (getArguments() != null) {
            sheetMusic = getArguments().getParcelable("sheet_music");
        }

        // Initialize audio picker launcher
        audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri audioUri = result.getData().getData();
                        if (audioUri != null) {
                            // Add the audio file to the sheet music
                            addAudioFile(audioUri);
                        }
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Create a dialog
        dialog = new Dialog(requireContext());

        View view = LayoutInflater.from(getContext()).inflate(R.layout.sheet_metadata_dialog, null);
        dialog.setContentView(view);

        // Configure dialog window for full screen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Initialize views
        initializeViews(view);

        // Setup click listeners
        setupListeners();

        // Set initial data
        updateUI(view); // Pass the view to updateUI

        // Show properties section by default
        showPropertiesContent();

        return dialog;
    }

    // Update the updateUI method to use the view parameter
    private void updateUI(View view) {
        // Update sheet name in header
        TextView sheetNameText = view.findViewById(R.id.sheet_name);
        if (sheetNameText != null) {
            sheetNameText.setText(sheetMusic.getTitle());
        }
    }

    private void initializeViews(View view) {
        // Header
        TextView sheetNameText = view.findViewById(R.id.sheet_name);
        sheetNameText.setText(sheetMusic.getTitle());

        // Content container
        contentContainer = view.findViewById(R.id.content_container);

        // Tab buttons
        propertiesButton = view.findViewById(R.id.properties_button);
        setlistButton = view.findViewById(R.id.setlist_button);
        audioButton = view.findViewById(R.id.audio_button);
        librariesButton = view.findViewById(R.id.libraries_button);
    }

    private void setupListeners() {
        propertiesButton.setOnClickListener(v -> {
            updateTabButtonStyles(propertiesButton);
            showPropertiesContent();
        });

        setlistButton.setOnClickListener(v -> {
            updateTabButtonStyles(setlistButton);
            showSetlistContent();
        });

        audioButton.setOnClickListener(v -> {
            updateTabButtonStyles(audioButton);
            showAudioContent();
        });

        librariesButton.setOnClickListener(v -> {
            updateTabButtonStyles(librariesButton);
            showLibrariesContent();
        });
    }

    private void updateTabButtonStyles(Button selectedButton) {
        // Reset all button styles
        propertiesButton.setTextColor(getResources().getColor(android.R.color.darker_gray));
        setlistButton.setTextColor(getResources().getColor(android.R.color.darker_gray));
        audioButton.setTextColor(getResources().getColor(android.R.color.darker_gray));
        librariesButton.setTextColor(getResources().getColor(android.R.color.darker_gray));

        // Highlight selected button
        selectedButton.setTextColor(getResources().getColor(android.R.color.black));
    }

    private void updateUI() {
        // Update sheet name in header
        TextView sheetNameText = dialog.findViewById(R.id.sheet_name);
        if (sheetNameText != null) {
            sheetNameText.setText(sheetMusic.getTitle());
        }
    }

    private void showSetlistContent() {
        contentContainer.removeAllViews();
        View setlistView = LayoutInflater.from(getContext()).inflate(R.layout.setlist_content, contentContainer, false);
        contentContainer.addView(setlistView);

        RecyclerView recyclerView = setlistView.findViewById(R.id.setlists_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Get setlists from database
        List<Setlist> setlists = getSetlistsFromDatabase();

        // Create adapter
        SetlistAdapter adapter = new SetlistAdapter(setlists, sheetMusic.getSetlists());
        recyclerView.setAdapter(adapter);
    }
    private void showPropertiesContent() {
        contentContainer.removeAllViews();
        View propertiesView = LayoutInflater.from(getContext()).inflate(R.layout.properties_content, contentContainer, false);
        contentContainer.addView(propertiesView);

        // Initialize properties views
        titleInput = propertiesView.findViewById(R.id.title_input);
        composersInput = propertiesView.findViewById(R.id.composers_input);
        genresInput = propertiesView.findViewById(R.id.genres_input);
        labelsInput = propertiesView.findViewById(R.id.labels_input);
        referencesInput = propertiesView.findViewById(R.id.references_input);
        ratingBar = propertiesView.findViewById(R.id.rating_bar);
        selectedKeyText = propertiesView.findViewById(R.id.selected_key_text);

        // Set file details
        TextView fileNameValue = propertiesView.findViewById(R.id.file_name_value);
        TextView fileSizeValue = propertiesView.findViewById(R.id.file_size_value);
        TextView pagesValue = propertiesView.findViewById(R.id.pages_value);

        fileNameValue.setText(sheetMusic.getFilename());
        fileSizeValue.setText(sheetMusic.getFormattedFileSize());
        pagesValue.setText(String.valueOf(sheetMusic.getPageCount()));

        // Set editable fields
        titleInput.setText(sheetMusic.getTitle());
        composersInput.setText(sheetMusic.getComposers());
        genresInput.setText(sheetMusic.getGenres());
        labelsInput.setText(sheetMusic.getLabels());
        referencesInput.setText(sheetMusic.getReferences());
        ratingBar.setRating(sheetMusic.getRating());
        selectedKeyText.setText(sheetMusic.getKey());

        // Setup key picker
        Button keyPickerButton = propertiesView.findViewById(R.id.key_picker_button);
        keyPickerButton.setOnClickListener(v -> showKeyPickerDialog());
    }

    private void showAudioContent() {
        contentContainer.removeAllViews();
        View audioView = LayoutInflater.from(getContext()).inflate(R.layout.audio_content, contentContainer, false);
        contentContainer.addView(audioView);

        RecyclerView recyclerView = audioView.findViewById(R.id.audio_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // No audio text
        TextView noAudioText = audioView.findViewById(R.id.no_audio_text);

        // Set visibility based on whether there are audio files
        if (sheetMusic.getAudioFiles().isEmpty()) {
            noAudioText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            noAudioText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        // Create adapter
        AudioAdapter adapter = new AudioAdapter(sheetMusic.getAudioFiles());
        recyclerView.setAdapter(adapter);

        // Setup audio control buttons
        View.OnClickListener deleteListener = v -> deleteSelectedAudioFiles(adapter);
        audioView.findViewById(R.id.delete_audio_button).setOnClickListener(deleteListener);

        audioView.findViewById(R.id.add_audio_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            audioPickerLauncher.launch(Intent.createChooser(intent, "Select Audio File"));
        });

        audioView.findViewById(R.id.dismiss_audio_button).setOnClickListener(v -> {
            // Just show another section
            updateTabButtonStyles(propertiesButton);
            showPropertiesContent();
        });
    }

    private void showLibrariesContent() {
        contentContainer.removeAllViews();
        View librariesView = LayoutInflater.from(getContext()).inflate(R.layout.libraries_content, contentContainer, false);
        contentContainer.addView(librariesView);

        RecyclerView recyclerView = librariesView.findViewById(R.id.libraries_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void showKeyPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View keyPickerView = LayoutInflater.from(getContext()).inflate(R.layout.key_picker_dialog, null);
        builder.setView(keyPickerView);

        // Get the preview text
        TextView keyPreview = keyPickerView.findViewById(R.id.key_preview);

        // Get radio groups
        RadioGroup noteGroup = keyPickerView.findViewById(R.id.note_group);
        RadioGroup accidentalGroup = keyPickerView.findViewById(R.id.accidental_group);
        RadioGroup scaleGroup = keyPickerView.findViewById(R.id.scale_group);

        // Set initial selection based on current key
        setInitialKeySelections(noteGroup, accidentalGroup, scaleGroup, sheetMusic.getKey());

        // Update preview when selection changes
        RadioGroup.OnCheckedChangeListener changeListener = (group, checkedId) -> {
            keyPreview.setText(getSelectedKey(noteGroup, accidentalGroup, scaleGroup));
        };

        noteGroup.setOnCheckedChangeListener(changeListener);
        accidentalGroup.setOnCheckedChangeListener(changeListener);
        scaleGroup.setOnCheckedChangeListener(changeListener);

        // Initialize with current selection
        keyPreview.setText(getSelectedKey(noteGroup, accidentalGroup, scaleGroup));

        // Setup buttons
        Button cancelButton = keyPickerView.findViewById(R.id.cancel_button);
        Button selectButton = keyPickerView.findViewById(R.id.select_button);

        final AlertDialog dialog = builder.create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        selectButton.setOnClickListener(v -> {
            String selectedKey = getSelectedKey(noteGroup, accidentalGroup, scaleGroup);
            selectedKeyText.setText(selectedKey);
            sheetMusic.setKey(selectedKey);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setInitialKeySelections(RadioGroup noteGroup, RadioGroup accidentalGroup, RadioGroup scaleGroup, String currentKey) {
        // Parse current key - example: "C Major", "F# Minor", "Bb Major"
        if (currentKey == null || currentKey.isEmpty()) {
            // Default to C Major
            ((RadioButton) noteGroup.findViewById(R.id.note_c)).setChecked(true);
            ((RadioButton) accidentalGroup.findViewById(R.id.accidental_none)).setChecked(true);
            ((RadioButton) scaleGroup.findViewById(R.id.scale_major)).setChecked(true);
            return;
        }

        // Split key into note and scale
        String[] parts = currentKey.split(" ");
        if (parts.length != 2) {
            return;
        }

        String note = parts[0];
        String scale = parts[1];

        // Handle note and accidental
        if (note.length() > 1) {
            // Note with accidental
            char baseNote = note.charAt(0);
            String accidental = note.substring(1);

            // Set base note
            selectBaseNote(noteGroup, baseNote);

            // Set accidental
            if (accidental.equals("#")) {
                ((RadioButton) accidentalGroup.findViewById(R.id.accidental_sharp)).setChecked(true);
            } else if (accidental.equals("b")) {
                ((RadioButton) accidentalGroup.findViewById(R.id.accidental_flat)).setChecked(true);
            }
        } else {
            // Just the base note
            selectBaseNote(noteGroup, note.charAt(0));
            ((RadioButton) accidentalGroup.findViewById(R.id.accidental_none)).setChecked(true);
        }

        // Set scale
        if (scale.equalsIgnoreCase("Minor")) {
            ((RadioButton) scaleGroup.findViewById(R.id.scale_minor)).setChecked(true);
        } else {
            ((RadioButton) scaleGroup.findViewById(R.id.scale_major)).setChecked(true);
        }
    }

    private void selectBaseNote(RadioGroup noteGroup, char baseNote) {
        int noteId;
        switch (Character.toUpperCase(baseNote)) {
            case 'C':
                noteId = R.id.note_c;
                break;
            case 'D':
                noteId = R.id.note_d;
                break;
            case 'E':
                noteId = R.id.note_e;
                break;
            case 'F':
                noteId = R.id.note_f;
                break;
            case 'G':
                noteId = R.id.note_g;
                break;
            case 'A':
                noteId = R.id.note_a;
                break;
            case 'B':
                noteId = R.id.note_b;
                break;
            default:
                noteId = R.id.note_c;
                break;
        }
        ((RadioButton) noteGroup.findViewById(noteId)).setChecked(true);
    }

    private String getSelectedKey(RadioGroup noteGroup, RadioGroup accidentalGroup, RadioGroup scaleGroup) {
        // Get the selected note
        int selectedNoteId = noteGroup.getCheckedRadioButtonId();
        RadioButton selectedNote = noteGroup.findViewById(selectedNoteId);
        String note = selectedNote.getText().toString();

        // Get the selected accidental
        int selectedAccidentalId = accidentalGroup.getCheckedRadioButtonId();
        String accidental = "";
        if (selectedAccidentalId == R.id.accidental_sharp) {
            accidental = "#";
        } else if (selectedAccidentalId == R.id.accidental_flat) {
            accidental = "b";
        }

        // Get the selected scale
        int selectedScaleId = scaleGroup.getCheckedRadioButtonId();
        RadioButton selectedScale = scaleGroup.findViewById(selectedScaleId);
        String scale = selectedScale.getText().toString();

        return note + accidental + " " + scale;
    }

    private void saveChanges() {
        // Save properties
        if (titleInput != null) {
            sheetMusic.setTitle(titleInput.getText().toString());
            sheetMusic.setComposers(composersInput.getText().toString());
            sheetMusic.setGenres(genresInput.getText().toString());
            sheetMusic.setLabels(labelsInput.getText().toString());
            sheetMusic.setReferences(referencesInput.getText().toString());
            sheetMusic.setRating(ratingBar.getRating());

            // Update UI
            updateUI();

            // Notify listener
            if (listener != null) {
                listener.onMetadataUpdated(sheetMusic);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save changes when dialog is dismissed or paused
        saveChanges();
    }

    private void addAudioFile(Uri audioUri) {
        // In a real implementation, you would:
        // 1. Copy or reference the audio file
        // 2. Add it to the sheet music's audio files
        // 3. Update the UI

        // For this example, we'll create a mock AudioFile
        String fileName = getFileNameFromUri(audioUri);
        long fileSize = getFileSizeFromUri(audioUri);
        String duration = "3:45"; // In a real implementation, you would get the actual duration

        AudioFile audioFile = new AudioFile(fileName, fileSize, duration, audioUri.toString());
        sheetMusic.addAudioFile(audioFile);

        // Refresh the audio content view
        if (audioButton.getCurrentTextColor() == getResources().getColor(android.R.color.black)) {
            // Audio tab is currently visible
            showAudioContent();
        }

        Toast.makeText(getContext(), "Added audio file: " + fileName, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedAudioFiles(AudioAdapter adapter) {
        List<AudioFile> selectedFiles = adapter.getSelectedFiles();
        if (selectedFiles.isEmpty()) {
            Toast.makeText(getContext(), "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove selected files from sheet music
        for (AudioFile file : selectedFiles) {
            sheetMusic.removeAudioFile(file);
        }

        // Refresh the audio content view
        showAudioContent();

        Toast.makeText(getContext(), selectedFiles.size() + " file(s) removed", Toast.LENGTH_SHORT).show();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = uri.getLastPathSegment();
        if (result.contains("/")) {
            result = result.substring(result.lastIndexOf("/") + 1);
        }
        return result;
    }

    private long getFileSizeFromUri(Uri uri) {
        try {
            return getContext().getContentResolver().openFileDescriptor(uri, "r").getStatSize();
        } catch (Exception e) {
            return 0;
        }
    }

    // Mock data methods - would be replaced with actual database calls
    private List<Setlist> getSetlistsFromDatabase() {
        List<Setlist> setlists = new ArrayList<>();
        setlists.add(new Setlist(1, "Classical Favorites", 12));
        setlists.add(new Setlist(2, "Practice Routine", 8));
        setlists.add(new Setlist(3, "Recital Program", 5));
        setlists.add(new Setlist(4, "Teaching Material", 15));
        setlists.add(new Setlist(5, "Baroque Collection", 10));
        return setlists;
    }

    // Adapter classes for RecyclerViews
    private class SetlistAdapter extends RecyclerView.Adapter<SetlistAdapter.SetlistViewHolder> {
        private List<Setlist> setlists;
        private List<Integer> selectedSetlistIds;

        public SetlistAdapter(List<Setlist> setlists, List<Integer> selectedSetlistIds) {
            this.setlists = setlists;
            this.selectedSetlistIds = selectedSetlistIds;
        }

        @NonNull
        @Override
        public SetlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.setlist_item, parent, false);
            return new SetlistViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SetlistViewHolder holder, int position) {
            Setlist setlist = setlists.get(position);
            holder.nameView.setText(setlist.name);
            holder.countView.setText(setlist.itemCount + " items");

            // Set checkbox state
            holder.checkBox.setChecked(selectedSetlistIds.contains(setlist.id));

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                // Toggle checkbox
                boolean newState = !holder.checkBox.isChecked();
                holder.checkBox.setChecked(newState);

                // Update selected setlists
                if (newState) {
                    if (!selectedSetlistIds.contains(setlist.id)) {
                        selectedSetlistIds.add(setlist.id);
                    }
                } else {
                    selectedSetlistIds.remove(Integer.valueOf(setlist.id));
                }

                // Update sheet music
                sheetMusic.setSetlists(selectedSetlistIds);
            });
        }

        @Override
        public int getItemCount() {
            return setlists.size();
        }

        class SetlistViewHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            TextView countView;
            android.widget.CheckBox checkBox;

            SetlistViewHolder(View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.setlist_name);
                countView = itemView.findViewById(R.id.setlist_count);
                checkBox = itemView.findViewById(R.id.setlist_checkbox);
            }
        }
    }

    private static class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder> {
        private final List<Library> libraries;
        private final List<Long> selectedLibraryIds;

        public LibraryAdapter(List<Library> libraries, List<Long> selectedLibraryIds) {
            this.libraries = libraries;
            this.selectedLibraryIds = selectedLibraryIds;
        }

        @NonNull
        @Override
        public LibraryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.library_item, parent, false);
            return new LibraryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LibraryViewHolder holder, int position) {
            Library library = libraries.get(position);
            holder.nameView.setText(library.getName());
            holder.countView.setText(library.getSheets().size() + " items");

            // Set checkbox state
            holder.checkBox.setChecked(selectedLibraryIds.contains(library.getId()));

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                // Toggle checkbox
                boolean newState = !holder.checkBox.isChecked();
                holder.checkBox.setChecked(newState);

                // Update selected libraries
                if (newState) {
                    if (!selectedLibraryIds.contains(library.getId())) {
                        selectedLibraryIds.add(library.getId());
                    }
                } else {
                    selectedLibraryIds.remove(library.getId());
                }

            });
        }

        @Override
        public int getItemCount() {
            return libraries.size();
        }

        class LibraryViewHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            TextView countView;
            android.widget.CheckBox checkBox;

            LibraryViewHolder(View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.library_name);
                countView = itemView.findViewById(R.id.library_count);
                checkBox = itemView.findViewById(R.id.library_checkbox);
            }
        }
    }

    private class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {
        private List<AudioFile> audioFiles;
        private List<AudioFile> selectedFiles = new ArrayList<>();

        public AudioAdapter(List<AudioFile> audioFiles) {
            this.audioFiles = audioFiles;
        }

        @NonNull
        @Override
        public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.audio_item, parent, false);
            return new AudioViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
            AudioFile audioFile = audioFiles.get(position);
            holder.nameView.setText(audioFile.getName());
            holder.detailsView.setText(audioFile.getDuration() + " • " + formatFileSize(audioFile.getSize()));

            // Set checkbox state
            holder.checkBox.setChecked(selectedFiles.contains(audioFile));

            // Set click listeners
            holder.itemView.setOnClickListener(v -> {
                // Toggle checkbox
                boolean newState = !holder.checkBox.isChecked();
                holder.checkBox.setChecked(newState);

                // Update selected files
                if (newState) {
                    if (!selectedFiles.contains(audioFile)) {
                        selectedFiles.add(audioFile);
                    }
                } else {
                    selectedFiles.remove(audioFile);
                }
            });

            holder.playButton.setOnClickListener(v -> {
                // Play the audio file
                Toast.makeText(getContext(), "Playing: " + audioFile.getName(), Toast.LENGTH_SHORT).show();
                // In a real implementation, you would start playback here
            });
        }

        @Override
        public int getItemCount() {
            return audioFiles.size();
        }

        public List<AudioFile> getSelectedFiles() {
            return selectedFiles;
        }

        private String formatFileSize(long size) {
            if (size <= 0) return "0 B";
            final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        private void showSheetPropertiesDialog(View view, SheetEntity item) {
            // Convert the SheetEntity to a SheetMusic object that the dialog can use
            SheetMusic sheetMusic = convertSheetEntityToSheetMusic(item);

            // Create and show the dialog
            SheetMetadataDialog dialog = SheetMetadataDialog.newInstance(sheetMusic);
            dialog.setListener(new SheetMetadataDialog.SheetMetadataListener() {
                @Override
                public void onMetadataUpdated(SheetMusic updatedSheet) {
                    // Update the database with the changes
                    SheetEntity updatedEntity = updateSheetEntityFromSheetMusic(item, updatedSheet);
                    // Get ViewModel from activity context
                    SheetViewModel viewModel = new ViewModelProvider((AppCompatActivity)view.getContext())
                            .get(SheetViewModel.class);
                    viewModel.updateSheet(updatedEntity);
                }
            });

            // Show the dialog
            dialog.show(((AppCompatActivity)view.getContext()).getSupportFragmentManager(), "metadata_dialog");
        }

        private SheetEntity updateSheetEntityFromSheetMusic(SheetEntity original, SheetMusic updated) {
            original.setId(updated.getId());
            original.setTitle(updated.getTitle());
            original.setComposers(updated.getComposers());
            original.setGenres(updated.getGenres());
            original.setLabels(updated.getLabels());
            original.setReferences(updated.getReferences());
            original.setRating(updated.getRating());
            original.setKey(updated.getKey());

            return original;
        }

        private SheetMusic convertSheetEntityToSheetMusic(SheetEntity entity) {
            SheetMusic sheetMusic = new SheetMusic(
                    entity.getId(),
                    entity.getTitle(),
                    entity.getFilename(),
                    entity.getFilePath(),
                    entity.getFileSize(),
                    entity.getPageCount()
            );
            sheetMusic.setId(entity.getId());
            sheetMusic.setComposers(entity.getComposers());
            sheetMusic.setGenres(entity.getGenres());
            sheetMusic.setLabels(entity.getLabels());
            sheetMusic.setReferences(entity.getReferences());
            sheetMusic.setRating(entity.getRating());
            sheetMusic.setKey(entity.getKey());

            // You'll need to also set setlists and libraries if available
            // For now, using empty lists
            sheetMusic.setSetlists(new ArrayList<>());

            return sheetMusic;
        }

        class AudioViewHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            TextView detailsView;
            android.widget.CheckBox checkBox;
            android.widget.ImageButton playButton;

            AudioViewHolder(View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.audio_name);
                detailsView = itemView.findViewById(R.id.audio_details);
                checkBox = itemView.findViewById(R.id.audio_checkbox);
                playButton = itemView.findViewById(R.id.play_audio_button);
            }
        }
    }

    public void setListener(SheetMetadataListener sheetMetadataListener) {
        listener = sheetMetadataListener;
    }
}
