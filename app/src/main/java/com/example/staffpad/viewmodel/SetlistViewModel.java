package com.example.staffpad.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.staffpad.database.SetlistEntity;
import com.example.staffpad.database.SetlistWithCount;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.repository.SetlistRepository;
import com.example.staffpad.database.repository.SheetRepository;


import java.util.List;

public class SetlistViewModel extends AndroidViewModel {

    private final SetlistRepository setlistRepository;
    private final SheetRepository sheetRepository;

    // LiveData for UI to observe
    private final LiveData<List<SetlistWithCount>> allSetlists;
    private final MutableLiveData<Integer> selectedSetlistId = new MutableLiveData<>();
    private final LiveData<SetlistEntity> selectedSetlist;
    private final LiveData<List<SheetEntity>> sheetsInSelectedSetlist;

    public SetlistViewModel(@NonNull Application application) {
        super(application);
        setlistRepository = new SetlistRepository(application);
        sheetRepository = new SheetRepository(application);

        allSetlists = setlistRepository.getSetlistsWithCount();

        // Setup transformations for the selected setlist
        selectedSetlist = Transformations.switchMap(selectedSetlistId,
                id -> setlistRepository.getSetlistById(id));

        // Setup transformations for the sheets in the selected setlist
        sheetsInSelectedSetlist = Transformations.switchMap(selectedSetlistId,
                id -> sheetRepository.getSheetsInSetlist(id));
    }

    // Setlist operations
    public LiveData<List<SetlistWithCount>> getAllSetlists() {
        return allSetlists;
    }

    public LiveData<List<SetlistEntity>> getSetlistsByRecent() {
        return setlistRepository.getSetlistsByRecent();
    }

    public LiveData<List<SetlistEntity>> searchSetlists(String query) {
        return setlistRepository.searchSetlists(query);
    }

    public void insertSetlist(SetlistEntity setlist) {
        setlistRepository.insertSetlist(setlist);
    }

    public void updateSetlist(SetlistEntity setlist) {
        setlistRepository.updateSetlist(setlist);
    }

    public void deleteSetlist(SetlistEntity setlist) {
        setlistRepository.deleteSetlist(setlist);
    }

    // Selected setlist operations
    public void selectSetlist(int setlistId) {
        selectedSetlistId.setValue(setlistId);
    }

    public LiveData<SetlistEntity> getSelectedSetlist() {
        return selectedSetlist;
    }

    public LiveData<List<SheetEntity>> getSheetsInSelectedSetlist() {
        return sheetsInSelectedSetlist;
    }

    // Sheet operations within the selected setlist
    public void addSheetToSelectedSetlist(int sheetId) {
        Integer setlistId = selectedSetlistId.getValue();
        if (setlistId != null) {
            sheetRepository.addSheetToSetlist(sheetId, setlistId);
        }
    }

    public void removeSheetFromSelectedSetlist(int sheetId) {
        Integer setlistId = selectedSetlistId.getValue();
        if (setlistId != null) {
            sheetRepository.removeSheetFromSetlist(sheetId, setlistId);
        }
    }

    public void reorderSelectedSetlist(List<Integer> newOrder) {
        Integer setlistId = selectedSetlistId.getValue();
        if (setlistId != null) {
            setlistRepository.reorderSetlist(setlistId, newOrder);
        }
    }

    // Helper methods
    public boolean isSheetInSelectedSetlist(int sheetId) {
        Integer setlistId = selectedSetlistId.getValue();
        if (setlistId != null) {
            return sheetRepository.isSheetInSetlist(sheetId, setlistId);
        }
        return false;
    }
}