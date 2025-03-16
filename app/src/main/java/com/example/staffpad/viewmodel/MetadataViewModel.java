package com.example.staffpad.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.staffpad.database.AudioEntity;
import com.example.staffpad.database.SetlistWithCount;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.repository.SetlistRepository;
import com.example.staffpad.database.repository.SheetRepository;


import java.util.List;

/**
 * ViewModel for the Sheet Metadata Dialog, providing access to all the sheet-related data
 * needed for the metadata editing UI.
 */
public class MetadataViewModel extends AndroidViewModel {

    private final SheetRepository sheetRepository;
    private final SetlistRepository setlistRepository;

    // LiveData for the sheet being edited
    private final MutableLiveData<Integer> editingSheetId = new MutableLiveData<>();
    private final LiveData<SheetEntity> editingSheet;
    private final LiveData<List<AudioEntity>> sheetAudioFiles;
    private final LiveData<List<Integer>> sheetSetlists;

    // LiveData for all setlists and libraries (for selection)
    private final LiveData<List<SetlistWithCount>> allSetlists;

    public MetadataViewModel(@NonNull Application application) {
        super(application);
        sheetRepository = new SheetRepository(application);
        setlistRepository = new SetlistRepository(application);

        // Setup the sheet being edited
        editingSheet = Transformations.switchMap(editingSheetId,
                id -> sheetRepository.getSheetById(id));

        // Setup related data for the sheet
        sheetAudioFiles = Transformations.switchMap(editingSheetId,
                id -> sheetRepository.getAudioFilesForSheet(id));

        sheetSetlists = Transformations.switchMap(editingSheetId,
                id -> sheetRepository.getSetlistsForSheet(id));

        // Get all setlists and libraries for selection
        allSetlists = setlistRepository.getSetlistsWithCount();
    }

    // Setup the sheet being edited
    public void setEditingSheet(int sheetId) {
        editingSheetId.setValue(sheetId);
    }

    public LiveData<SheetEntity> getEditingSheet() {
        return editingSheet;
    }

    // Update sheet properties
    public void updateSheet(SheetEntity updatedSheet) {
        sheetRepository.updateSheet(updatedSheet);
    }

    public void updateSheetTitle(String title) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setTitle(title);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetComposers(String composers) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setComposers(composers);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetGenres(String genres) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setGenres(genres);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetLabels(String labels) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setLabels(labels);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetReferences(String references) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setReferences(references);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetRating(float rating) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setRating(rating);
            sheetRepository.updateSheet(sheet);
        }
    }

    public void updateSheetKey(String key) {
        SheetEntity sheet = editingSheet.getValue();
        if (sheet != null) {
            sheet.setKey(key);
            sheetRepository.updateSheet(sheet);
        }
    }

    // Audio operations
    public LiveData<List<AudioEntity>> getSheetAudioFiles() {
        return sheetAudioFiles;
    }

    public void addAudioToSheet(Uri audioUri) {
        Integer sheetId = editingSheetId.getValue();
        if (sheetId != null) {
            sheetRepository.addAudioToSheet(sheetId, audioUri);
        }
    }

    public void removeAudioFromSheet(int audioId) {
        sheetRepository.removeAudioFromSheet(audioId);
    }

    // Setlist operations
    public LiveData<List<SetlistWithCount>> getAllSetlists() {
        return allSetlists;
    }

    public LiveData<List<Integer>> getSheetSetlists() {
        return sheetSetlists;
    }

    public void toggleSheetInSetlist(int setlistId, boolean add) {
        Integer sheetId = editingSheetId.getValue();
        if (sheetId != null) {
            if (add) {
                sheetRepository.addSheetToSetlist(sheetId, setlistId);
            } else {
                sheetRepository.removeSheetFromSetlist(sheetId, setlistId);
            }
        }
    }

    public boolean isSheetInSetlist(int setlistId) {
        Integer sheetId = editingSheetId.getValue();
        if (sheetId != null) {
            return sheetRepository.isSheetInSetlist(sheetId, setlistId);
        }
        return false;
    }

    // Helper methods for auto-complete
    public LiveData<List<String>> getAllComposers() {
        return sheetRepository.getAllComposers();
    }

    public LiveData<List<String>> getAllGenres() {
        return sheetRepository.getAllGenres();
    }

    public LiveData<List<String>> getAllLabels() {
        return sheetRepository.getAllLabels();
    }
}