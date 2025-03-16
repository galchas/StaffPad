package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class SheetWithRelations {
    @Embedded
    public SheetEntity sheet;

    @Relation(
            parentColumn = "id",
            entityColumn = "sheet_id",
            entity = AudioEntity.class
    )
    public List<AudioEntity> audioFiles;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = SheetSetlistCrossRef.class,
                    parentColumn = "sheet_id",
                    entityColumn = "setlist_id"
            )
    )
    public List<SetlistEntity> setlists;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = SheetTagCrossRef.class,
                    parentColumn = "sheet_id",
                    entityColumn = "tag_id"
            )
    )
    public List<TagEntity> tags;
}