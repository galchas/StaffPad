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
public interface SheetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSheet(SheetEntity sheet);

    @Update
    void updateSheet(SheetEntity sheet);

    @Delete
    void deleteSheet(SheetEntity sheet);

    @Query("SELECT * FROM sheets WHERE id = :id")
    LiveData<SheetEntity> getSheetById(long id);

    @Query("SELECT * FROM sheets ORDER BY title ASC")
    LiveData<List<SheetEntity>> getAllSheets();

    @Query("SELECT * FROM sheets ORDER BY last_opened_at DESC LIMIT :limit")
    LiveData<List<SheetEntity>> getRecentSheets(int limit);

    @Query("SELECT * FROM sheets WHERE composers LIKE '%' || :composer || '%' ORDER BY title ASC")
    LiveData<List<SheetEntity>> getSheetsByComposer(String composer);

    @Query("SELECT * FROM sheets WHERE genres LIKE '%' || :genre || '%' ORDER BY title ASC")
    LiveData<List<SheetEntity>> getSheetsByGenre(String genre);

    @Query("SELECT * FROM sheets WHERE labels LIKE '%' || :label || '%' ORDER BY title ASC")
    LiveData<List<SheetEntity>> getSheetsByLabel(String label);

    @Query("SELECT * FROM sheets WHERE title LIKE '%' || :query || '%' OR composers LIKE '%' || :query || '%' ORDER BY title ASC")
    LiveData<List<SheetEntity>> searchSheets(String query);

    @Query("UPDATE sheets SET last_opened_at = :timestamp WHERE id = :sheetId")
    void updateLastOpenedAt(long sheetId, long timestamp);

    @Query("SELECT DISTINCT composers FROM sheets WHERE composers IS NOT NULL AND composers != ''")
    LiveData<List<String>> getAllComposers();

    @Query("SELECT DISTINCT genres FROM sheets WHERE genres IS NOT NULL AND genres != ''")
    LiveData<List<String>> getAllGenres();

    @Query("SELECT DISTINCT labels FROM sheets WHERE labels IS NOT NULL AND labels != ''")
    LiveData<List<String>> getAllLabels();

    @Transaction
    @Query("SELECT s.* FROM sheets s INNER JOIN sheet_setlist_cross_ref ss ON s.id = ss.sheet_id WHERE ss.setlist_id = :setlistId ORDER BY ss.position ASC")
    LiveData<List<SheetEntity>> getSheetsInSetlist(long setlistId);

    @Transaction
    @Query("SELECT * FROM sheets " +
            "INNER JOIN sheet_tag_cross_ref ON sheets.id = sheet_tag_cross_ref.sheet_id " +
            "WHERE sheet_tag_cross_ref.tag_id = :tagId " +
            "ORDER BY title ASC")
    LiveData<List<SheetWithRelations>> getSheetsForTag(long tagId);

    @Transaction
    @Query("SELECT * FROM sheets ORDER BY title ASC")
    LiveData<List<SheetWithRelations>> getAllSheetsWithRelations();

    @Transaction
    @Query("SELECT * FROM sheets WHERE id = :sheetId")
    LiveData<SheetWithRelations> getSheetWithRelationsById(long sheetId);

    @Transaction
    @Query("SELECT * FROM sheets " +
            "INNER JOIN sheet_setlist_cross_ref ON sheets.id = sheet_setlist_cross_ref.sheet_id " +
            "WHERE sheet_setlist_cross_ref.setlist_id = :setlistId " +
            "ORDER BY title ASC")
    LiveData<List<SheetWithRelations>> getSheetsForSetlist(long setlistId);

    @Transaction
    @Query("SELECT * FROM sheets ORDER BY title ASC")
    List<SheetWithRelations> getAllSheetsWithRelationsSync();
}