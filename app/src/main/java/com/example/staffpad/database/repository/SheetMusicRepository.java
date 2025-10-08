package com.example.staffpad.database.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.example.staffpad.data_model.SheetMusic;
import com.example.staffpad.data_model.SheetMusicConverter;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.SheetDao;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.SheetWithRelations;
import com.example.staffpad.database.TagDao;
import com.example.staffpad.database.SheetTagCrossRef;
import com.example.staffpad.database.TagEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SheetMusicRepository {
    private final SheetDao sheetDao;
    private final TagDao tagDao;
    private final AppDatabase database;

    public SheetMusicRepository(Application application) {
        database = AppDatabase.getDatabase(application);
        sheetDao = database.sheetDao();
        tagDao = database.tagDao();
    }

    // Get all SheetMusic with all relations (including tags)
    // In SheetMusicRepository
    public LiveData<List<SheetMusic>> getAllSheetMusicWithRelations() {
        Log.d("SheetMusicRepository", "Requesting all sheets with relations");

        return Transformations.map(sheetDao.getAllSheetsWithRelations(),
                sheetWithRelationsList -> {
                    if (sheetWithRelationsList == null) {
                        Log.d("SheetMusicRepository", "Sheet list is null");
                        return null;
                    }

                    Log.d("SheetMusicRepository", "Got " + sheetWithRelationsList.size() + " sheets");

                    List<SheetMusic> result = sheetWithRelationsList.stream()
                            .map(SheetMusicConverter::fromSheetWithRelations)
                            .collect(Collectors.toList());

                    Log.d("SheetMusicRepository", "Converted " + result.size() + " sheets");
                    return result;
                }
        );
    }

    // Get SheetMusic by ID with all relations (including tags)
    public LiveData<SheetMusic> getSheetMusicWithRelationsById(long sheetId) {
        return Transformations.map(sheetDao.getSheetWithRelationsById(sheetId),
                SheetMusicConverter::fromSheetWithRelations
        );
    }

    // Get all SheetMusic for a specific setlist
    public LiveData<List<SheetMusic>> getSheetMusicForSetlist(long setlistId) {
        return Transformations.map(sheetDao.getSheetsForSetlist(setlistId),
                sheetWithRelationsList -> sheetWithRelationsList.stream()
                        .map(SheetMusicConverter::fromSheetWithRelations)
                        .collect(Collectors.toList())
        );
    }

    public List<SheetMusic> getAllSheetMusicWithRelationsSync() {
        // Create a holder for the result
        final List<SheetMusic>[] result = new List[1];

        // Create a CountDownLatch to wait for the operation to complete
        CountDownLatch latch = new CountDownLatch(1);

        // Execute the database query on the database's background thread
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<SheetWithRelations> sheetWithRelationsList = sheetDao.getAllSheetsWithRelationsSync();

                if (sheetWithRelationsList != null) {
                    result[0] = sheetWithRelationsList.stream()
                            .map(SheetMusicConverter::fromSheetWithRelations)
                            .collect(Collectors.toList());
                } else {
                    result[0] = new ArrayList<>();
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            // Wait for the operation to complete (with timeout)
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e("SheetMusicRepository", "Timeout waiting for database operation");
                return new ArrayList<>();
            }
        } catch (InterruptedException e) {
            Log.e("SheetMusicRepository", "Interrupted while waiting for database operation", e);
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }

        return result[0] != null ? result[0] : new ArrayList<>();
    }

    // Get all SheetMusic for a specific tag
    public LiveData<List<SheetMusic>> getSheetMusicForTag(long tagId) {
        return Transformations.map(sheetDao.getSheetsForTag(tagId),
                sheetWithRelationsList -> sheetWithRelationsList.stream()
                        .map(SheetMusicConverter::fromSheetWithRelations)
                        .collect(Collectors.toList())
        );
    }

    // Add a tag to a sheet
    public void addTagToSheet(long sheetId, long tagId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            tagDao.insertSheetTagCrossRef(new SheetTagCrossRef(sheetId, tagId));
        });
    }

    // Remove a tag from a sheet
    public void removeTagFromSheet(long sheetId, long tagId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            tagDao.deleteSheetTagCrossRef(new SheetTagCrossRef(sheetId, tagId));
        });
    }

    // Get all tags
    public LiveData<List<TagEntity>> getAllTags() {
        return tagDao.getAllTags();
    }

    // Get tags for a specific sheet
    public LiveData<List<TagEntity>> getTagsForSheet(long sheetId) {
        return tagDao.getTagsForSheet(sheetId);
    }

    // Add a new tag
    public void insertTag(TagEntity tag) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            tagDao.insertTag(tag);
        });
    }

    // Delete a tag
    public void deleteTag(TagEntity tag) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            tagDao.deleteTag(tag);
        });
    }

    public LiveData<SheetMusic> getSheetMusicById(long sheetId) {
        // Use MediatorLiveData to transform Entity to SheetMusic object
        MediatorLiveData<SheetMusic> result = new MediatorLiveData<>();

        // First get the basic sheet entity
        LiveData<SheetEntity> sheetEntityLiveData = database.sheetDao().getSheetById(sheetId);

        result.addSource(sheetEntityLiveData, sheetEntity -> {
            if (sheetEntity == null) {
                result.setValue(null);
                return;
            }

            // Create SheetMusic object from entity
            SheetMusic sheetMusic = new SheetMusic(sheetEntity);

            // Load related data (assuming these are your related tables)
            // Note: In a production app, you'd want to load these more efficiently with a single query

            // Add any additional relation loading here...
            // For example:
            // sheetMusic.setTags(getTagsForSheet(sheetId));

            result.setValue(sheetMusic);
        });

        return result;
    }
}