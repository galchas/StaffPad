package com.example.staffpad.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertLibrary(LibraryEntity library);

    @Update
    void updateLibrary(LibraryEntity library);

    @Delete
    void deleteLibrary(LibraryEntity library);

    @Query("SELECT * FROM libraries WHERE id = :libraryId")
    LiveData<LibraryEntity> getLibraryById(long libraryId);

    @Query("SELECT * FROM libraries ORDER BY name ASC")
    LiveData<List<LibraryEntity>> getAllLibraries();

    @Insert
    void insertSheetLibraryCrossRef(SheetLibraryCrossRef crossRef);

    @Delete
    void deleteSheetLibraryCrossRef(SheetLibraryCrossRef crossRef);

    @Query("DELETE FROM sheet_library_cross_ref WHERE library_id = :libraryId")
    void deleteAllSheetsFromLibrary(long libraryId);

    @Transaction
    @Query("SELECT * FROM libraries WHERE id = :libraryId")
    LiveData<LibraryWithSheets> getLibraryWithSheets(long libraryId);

    @Transaction
    @Query("SELECT * FROM libraries ORDER BY name ASC")
    LiveData<List<LibraryWithSheets>> getAllLibrariesWithSheets();
}