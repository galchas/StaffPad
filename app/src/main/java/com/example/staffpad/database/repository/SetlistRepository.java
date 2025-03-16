package com.example.staffpad.database.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.SetlistDao;
import com.example.staffpad.database.SetlistEntity;
import com.example.staffpad.database.SetlistWithCount;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class SetlistRepository {
    private final SetlistDao setlistDao;
    private final ExecutorService executorService;

    public SetlistRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.setlistDao = db.setlistDao();
        this.executorService = AppDatabase.databaseWriteExecutor;
    }

    // Setlist CRUD operations
    public void insertSetlist(SetlistEntity setlist) {
        executorService.execute(() -> {
            setlistDao.insertSetlist(setlist);
        });
    }

    public void updateSetlist(SetlistEntity setlist) {
        executorService.execute(() -> {
            setlist.setModifiedAt(System.currentTimeMillis());
            setlistDao.updateSetlist(setlist);
        });
    }

    public void deleteSetlist(SetlistEntity setlist) {
        executorService.execute(() -> {
            setlistDao.deleteSetlist(setlist);
        });
    }

    public LiveData<SetlistEntity> getSetlistById(int id) {
        return setlistDao.getSetlistById(id);
    }

    public LiveData<List<SetlistEntity>> getAllSetlists() {
        return setlistDao.getAllSetlists();
    }

    public LiveData<List<SetlistEntity>> getSetlistsByRecent() {
        return setlistDao.getSetlistsByRecent();
    }

    public LiveData<List<SetlistWithCount>> getSetlistsWithCount() {
        return setlistDao.getSetlistsWithCount();
    }

    public LiveData<List<SetlistEntity>> searchSetlists(String query) {
        return setlistDao.searchSetlists(query);
    }

    public int getSetlistItemCount(int setlistId) {
        return setlistDao.getSetlistItemCount(setlistId);
    }

    public void reorderSetlist(int setlistId, List<Integer> newOrder) {
        executorService.execute(() -> {
            // Get the current order
            List<Integer> currentSheetIds = setlistDao.getSheetIdsInSetlist(setlistId);

            // Check if the lists have the same elements (ignoring order)
            if (currentSheetIds.size() != newOrder.size() || !currentSheetIds.containsAll(newOrder)) {
                return; // Invalid reordering
            }

            // Update positions
            for (int i = 0; i < newOrder.size(); i++) {
                int sheetId = newOrder.get(i);
                setlistDao.updateSheetPositionInSetlist(sheetId, setlistId, i);
            }

            // Update modification timestamp
            setlistDao.updateModifiedAt(setlistId, System.currentTimeMillis());
        });
    }
}