package com.example.staffpad.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "audio_files",
        foreignKeys = {
                @ForeignKey(
                        entity = SheetEntity.class,
                        parentColumns = "id",
                        childColumns = "sheet_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("sheet_id")
        }
)
public class AudioEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "sheet_id")
    private long sheetId;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "file_path")
    private String filePath;

    @ColumnInfo(name = "uri")
    private String uri;

    @ColumnInfo(name = "size")
    private long size;

    @ColumnInfo(name = "duration")
    private long duration;

    @ColumnInfo(name = "duration_formatted")
    private String durationFormatted;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    // Constructor
    public AudioEntity(long sheetId, String name, String filePath, String uri, long size, long duration, String durationFormatted) {
        this.sheetId = sheetId;
        this.name = name;
        this.filePath = filePath;
        this.uri = uri;
        this.size = size;
        this.duration = duration;
        this.durationFormatted = durationFormatted;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSheetId() {
        return sheetId;
    }

    public void setSheetId(int sheetId) {
        this.sheetId = sheetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getDuration() {
        return duration;
    }

    public String getDurationString() {
        return formatDuration(duration);
    }

    public String formatDuration(long durationMs) {
        long minutes = (durationMs / 1000) / 60;
        long seconds = (durationMs / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getDurationFormatted() {
        return durationFormatted;
    }

    public void setDurationFormatted(String durationFormatted) {
        this.durationFormatted = durationFormatted;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}