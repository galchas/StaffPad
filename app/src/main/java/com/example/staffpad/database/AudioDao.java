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
public interface AudioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAudio(AudioEntity audio);

    @Update
    void updateAudio(AudioEntity audio);

    @Delete
    void deleteAudio(AudioEntity audio);

    // LiveData fetch (UI observe)
    @Query("SELECT * FROM audio_files WHERE id = :id")
    LiveData<AudioEntity> getAudioById(long id);

    // Synchronous fetch (repository background thread)
    @Query("SELECT * FROM audio_files WHERE id = :id")
    AudioEntity getAudioByIdSync(long id);

    // Delete by id (no need to load entity)
    @Query("DELETE FROM audio_files WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM audio_files WHERE sheet_id = :sheetId ORDER BY name ASC")
    LiveData<List<AudioEntity>> getAudioFilesForSheet(long sheetId);

    @Query("SELECT COUNT(*) FROM audio_files WHERE sheet_id = :sheetId")
    int getAudioCountForSheet(long sheetId);

    @Query("SELECT COUNT(*) FROM audio_files WHERE sheet_id = :sheetId AND (uri LIKE '%youtube.com/watch%' OR uri LIKE '%youtu.be/%')")
    int countYouTubeLinksForSheet(long sheetId);

    @Query("DELETE FROM audio_files WHERE sheet_id = :sheetId")
    void deleteAllAudioForSheet(long sheetId);
}