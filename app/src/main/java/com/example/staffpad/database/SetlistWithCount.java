package com.example.staffpad.database;

import androidx.room.Embedded; /**
 * Class to represent a Setlist with its sheet count
 */
public class SetlistWithCount {
    @Embedded
    public SetlistEntity setlist;

    public int item_count;
}
