package com.example.staffpad.database.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.staffpad.data_model.Library;
import com.example.staffpad.data_model.LibraryConverter;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.LibraryDao;
import com.example.staffpad.database.LibraryEntity;
import com.example.staffpad.database.SheetLibraryCrossRef;

import java.util.List;
import java.util.stream.Collectors;

public class LibraryRepository {
    private final LibraryDao libraryDao;
    private final AppDatabase database;

    public LibraryRepository(Application application) {
        database = AppDatabase.getDatabase(application);
        libraryDao = database.libraryDao();
    }

    // Get all libraries with their sheets
    public LiveData<List<Library>> getAllLibrariesWithSheets() {
        return Transformations.map(libraryDao.getAllLibrariesWithSheets(),
                libraryWithSheetsList -> libraryWithSheetsList.stream()
                        .map(LibraryConverter::fromLibraryWithSheets)
                        .collect(Collectors.toList())
        );
    }

    // Get a specific library with its sheets
    public LiveData<Library> getLibraryWithSheets(long libraryId) {
        return Transformations.map(libraryDao.getLibraryWithSheets(libraryId),
                LibraryConverter::fromLibraryWithSheets
        );
    }

    // Get all libraries (without sheets)
    public LiveData<List<LibraryEntity>> getAllLibraries() {
        return libraryDao.getAllLibraries();
    }

    // Insert a new library
    public void insertLibrary(LibraryEntity library) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.insertLibrary(library);
        });
    }

    // Insert library and return ID
    public long insertLibrarySync(LibraryEntity library) {
        return libraryDao.insertLibrary(library);
    }

    // Update a library
    public void updateLibrary(LibraryEntity library) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.updateLibrary(library);
        });
    }

    // Delete a library
    public void deleteLibrary(LibraryEntity library) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.deleteLibrary(library);
        });
    }

    // Add a sheet to a library
    public void addSheetToLibrary(long sheetId, long libraryId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.insertSheetLibraryCrossRef(new SheetLibraryCrossRef(sheetId, libraryId));
        });
    }

    // Remove a sheet from a library
    public void removeSheetFromLibrary(long sheetId, long libraryId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.deleteSheetLibraryCrossRef(new SheetLibraryCrossRef(sheetId, libraryId));
        });
    }

    // Remove all sheets from a library
    public void removeAllSheetsFromLibrary(long libraryId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            libraryDao.deleteAllSheetsFromLibrary(libraryId);
        });
    }
}