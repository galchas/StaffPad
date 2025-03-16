package com.example.staffpad.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.ColumnInfo;

@Entity(
        tableName = "sheet_tag_cross_ref",
        primaryKeys = {"sheet_id", "tag_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = SheetEntity.class,
                        parentColumns = "id",
                        childColumns = "sheet_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = TagEntity.class,
                        parentColumns = "id",
                        childColumns = "tag_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"sheet_id"}),
                @Index(value = {"tag_id"})
        }
)
public class SheetTagCrossRef {
    @ColumnInfo(name = "sheet_id")
    public long sheetId;

    @ColumnInfo(name = "tag_id")
    public long tagId;

    public SheetTagCrossRef(long sheetId, long tagId) {
        this.sheetId = sheetId;
        this.tagId = tagId;
    }
}