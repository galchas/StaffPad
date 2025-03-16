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
public interface SetlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSetlist(SetlistEntity setlist);

    @Update
    void updateSetlist(SetlistEntity setlist);

    @Delete
    void deleteSetlist(SetlistEntity setlist);

    @Query("SELECT * FROM setlists WHERE id = :id")
    LiveData<SetlistEntity> getSetlistById(long id);

    @Query("SELECT * FROM setlists ORDER BY name ASC")
    LiveData<List<SetlistEntity>> getAllSetlists();

    @Query("SELECT * FROM setlists ORDER BY modified_at DESC")
    LiveData<List<SetlistEntity>> getSetlistsByRecent();

    @Query("SELECT * FROM setlists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    LiveData<List<SetlistEntity>> searchSetlists(String query);

    @Query("UPDATE setlists SET modified_at = :timestamp WHERE id = :setlistId")
    void updateModifiedAt(long setlistId, long timestamp);

    @Query("SELECT COUNT(*) FROM sheet_setlist_cross_ref WHERE setlist_id = :setlistId")
    int getSetlistItemCount(long setlistId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSheetSetlistCrossRef(SheetSetlistCrossRef crossRef);

    @Query("DELETE FROM sheet_setlist_cross_ref WHERE sheet_id = :sheetId AND setlist_id = :setlistId")
    void removeSheetFromSetlist(long sheetId, long setlistId);

    @Query("SELECT MAX(position) + 1 FROM sheet_setlist_cross_ref WHERE setlist_id = :setlistId")
    int getNextPositionInSetlist(long setlistId);

    @Query("SELECT setlist_id FROM sheet_setlist_cross_ref WHERE sheet_id = :sheetId")
    LiveData<List<Integer>> getSetlistsForSheet(long sheetId);

    @Query("SELECT EXISTS(SELECT 1 FROM sheet_setlist_cross_ref WHERE sheet_id = :sheetId AND setlist_id = :setlistId)")
    boolean isSheetInSetlist(long sheetId, long setlistId);

    @Transaction
    @Query("SELECT s.*, COUNT(ss.sheet_id) as item_count FROM setlists s LEFT JOIN sheet_setlist_cross_ref ss ON s.id = ss.setlist_id GROUP BY s.id ORDER BY s.name ASC")
    LiveData<List<SetlistWithCount>> getSetlistsWithCount();

    @Query("UPDATE sheet_setlist_cross_ref SET position = :newPosition WHERE sheet_id = :sheetId AND setlist_id = :setlistId")
    void updateSheetPositionInSetlist(long sheetId, long setlistId, long newPosition);

    @Query("SELECT sheet_id FROM sheet_setlist_cross_ref WHERE setlist_id = :setlistId ORDER BY position ASC")
    List<Integer> getSheetIdsInSetlist(long setlistId);
}