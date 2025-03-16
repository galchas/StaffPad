package com.example.staffpad.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTag(TagEntity tag);

    @Update
    void updateTag(TagEntity tag);

    @Delete
    void deleteTag(TagEntity tag);

    @Query("SELECT * FROM tags WHERE id = :tagId")
    LiveData<TagEntity> getTagById(long tagId);

    @Query("SELECT * FROM tags ORDER BY name ASC")
    LiveData<List<TagEntity>> getAllTags();

    @Insert
    void insertSheetTagCrossRef(SheetTagCrossRef crossRef);

    @Delete
    void deleteSheetTagCrossRef(SheetTagCrossRef crossRef);

    @Query("SELECT * FROM tags " +
            "INNER JOIN sheet_tag_cross_ref ON tags.id = sheet_tag_cross_ref.tag_id " +
            "WHERE sheet_tag_cross_ref.sheet_id = :sheetId")
    LiveData<List<TagEntity>> getTagsForSheet(long sheetId);
}