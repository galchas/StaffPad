package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List; /**
 * Class to represent a Setlist with all its sheets
 */
public class SetlistWithSheets {
    @Embedded
    public SetlistEntity setlist;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = SheetSetlistCrossRef.class,
                    parentColumn = "setlist_id",
                    entityColumn = "sheet_id"
            )
    )
    public List<SheetEntity> sheets;
}
