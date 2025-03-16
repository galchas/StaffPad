package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List; /**
 * Class to represent a Sheet with all its setlists
 */
public class SheetWithSetlists {
    @Embedded
    public SheetEntity sheet;

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
