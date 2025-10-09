package com.example.staffpad.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PageLayerDao {
    @Insert
    long insert(PageLayerEntity layer);

    @Update
    void update(PageLayerEntity layer);

    @Delete
    void delete(PageLayerEntity layer);

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber AND is_active = 1 ORDER BY order_index ASC, id ASC")
    LiveData<List<PageLayerEntity>> getActiveLayersForPage(long sheetId, int pageNumber);

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber ORDER BY order_index ASC, id ASC")
    LiveData<List<PageLayerEntity>> getAllLayersForPage(long sheetId, int pageNumber);

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber")
    int getMaxOrderIndex(long sheetId, int pageNumber);

    @Query("UPDATE page_layers SET is_active = 0 WHERE sheet_id = :sheetId AND page_number = :pageNumber")
    void deactivateAllForPage(long sheetId, int pageNumber);

    @Query("UPDATE page_layers SET is_active = :active WHERE id = :layerId")
    void setActive(long layerId, boolean active);
}