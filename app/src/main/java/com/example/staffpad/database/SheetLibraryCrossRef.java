package com.example.staffpad.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.ColumnInfo;

@Entity(
        tableName = "sheet_library_cross_ref",
        primaryKeys = {"sheet_id", "library_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = SheetEntity.class,
                        parentColumns = "id",
                        childColumns = "sheet_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = LibraryEntity.class,
                        parentColumns = "id",
                        childColumns = "library_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"sheet_id"}),
                @Index(value = {"library_id"})
        }
)
public class SheetLibraryCrossRef {
    @ColumnInfo(name = "sheet_id")
    public long sheetId;

    @ColumnInfo(name = "library_id")
    public long libraryId;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    public SheetLibraryCrossRef(long sheetId, long libraryId) {
        this.sheetId = sheetId;
        this.libraryId = libraryId;
        this.addedAt = System.currentTimeMillis();
    }
}