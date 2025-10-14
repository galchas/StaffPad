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

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber AND is_active = 1 AND layer_type != 'CROP' ORDER BY order_index ASC, id ASC")
    LiveData<List<PageLayerEntity>> getActiveLayersForPage(long sheetId, int pageNumber);

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber AND layer_type != 'CROP' ORDER BY order_index ASC, id ASC")
    LiveData<List<PageLayerEntity>> getAllLayersForPage(long sheetId, int pageNumber);

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber AND is_active = 1 AND layer_type = 'ANNOTATION' ORDER BY order_index ASC, id ASC LIMIT 1")
    PageLayerEntity getActiveAnnotationLayer(long sheetId, int pageNumber);

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber")
    int getMaxOrderIndex(long sheetId, int pageNumber);

    @Query("UPDATE page_layers SET is_active = 0 WHERE sheet_id = :sheetId AND page_number = :pageNumber")
    void deactivateAllForPage(long sheetId, int pageNumber);

    @Query("UPDATE page_layers SET is_active = :active WHERE id = :layerId")
    void setActive(long layerId, boolean active);

    @Query("SELECT * FROM page_layers WHERE sheet_id = :sheetId AND page_number = :pageNumber AND layer_type = 'CROP' ORDER BY modified_at DESC, id DESC LIMIT 1")
    PageLayerEntity getLatestCropLayerForPage(long sheetId, int pageNumber);

    @Query("UPDATE page_layers SET is_active = 0 WHERE sheet_id = :sheetId AND page_number = :pageNumber AND layer_type = 'CROP' AND id != :keepId")
    void deactivateOtherCropLayers(long sheetId, int pageNumber, long keepId);
}