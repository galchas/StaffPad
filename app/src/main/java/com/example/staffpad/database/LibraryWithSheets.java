package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class LibraryWithSheets {
    @Embedded
    public LibraryEntity library;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = SheetLibraryCrossRef.class,
                    parentColumn = "library_id",
                    entityColumn = "sheet_id"
            )
    )
    public List<SheetEntity> sheets;
}