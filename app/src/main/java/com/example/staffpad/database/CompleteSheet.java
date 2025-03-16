package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List; /**
 * Class to represent a complete Sheet with all its relationships
 */
public class CompleteSheet {
    @Embedded
    public SheetEntity sheet;

    @Relation(
            parentColumn = "id",
            entityColumn = "sheet_id"
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

}