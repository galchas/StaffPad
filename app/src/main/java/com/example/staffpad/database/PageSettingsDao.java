package com.example.staffpad.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface PageSettingsDao {
    @Query("SELECT * FROM page_settings WHERE sheet_id = :sheetId AND page_number = :pageNumber LIMIT 1")
    PageSettingsEntity getByPage(long sheetId, int pageNumber);

    @Query("SELECT * FROM page_settings WHERE sheet_id = :sheetId AND page_number = :pageNumber LIMIT 1")
    LiveData<PageSettingsEntity> observeByPage(long sheetId, int pageNumber);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(PageSettingsEntity settings);

    @Update
    void update(PageSettingsEntity settings);
}
