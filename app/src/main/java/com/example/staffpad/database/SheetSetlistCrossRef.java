package com.example.staffpad.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.ColumnInfo;

@Entity(
        tableName = "sheet_setlist_cross_ref",
        primaryKeys = {"sheet_id", "setlist_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = SheetEntity.class,
                        parentColumns = "id",
                        childColumns = "sheet_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = SetlistEntity.class,
                        parentColumns = "id",
                        childColumns = "setlist_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("sheet_id"),
                @Index("setlist_id")
        }
)
public class SheetSetlistCrossRef {
    @ColumnInfo(name = "sheet_id")
    public long sheetId;

    @ColumnInfo(name = "setlist_id")
    public long setlistId;

    @ColumnInfo(name = "position")
    public int position;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    // Constructor
    public SheetSetlistCrossRef(long sheetId, long setlistId, int position) {
        this.sheetId = sheetId;
        this.setlistId = setlistId;
        this.position = position;
        this.addedAt = System.currentTimeMillis();
    }
}