package com.example.staffpad.database;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List; /**
 * Class to represent a Sheet with all its audio files
 */
public class SheetWithAudioFiles {
    @Embedded
    public SheetEntity sheet;

    @Relation(
            parentColumn = "id",
            entityColumn = "sheet_id"
    )
    public List<AudioEntity> audioFiles;
}
