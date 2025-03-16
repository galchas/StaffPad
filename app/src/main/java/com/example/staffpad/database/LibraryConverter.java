package com.example.staffpad.data_model;

import com.example.staffpad.database.LibraryWithSheets;
import com.example.staffpad.database.SheetEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LibraryConverter {

    public static Library fromLibraryWithSheets(LibraryWithSheets libraryWithSheets) {
        Library library = new Library(
                libraryWithSheets.library.getId(),
                libraryWithSheets.library.getName(),
                libraryWithSheets.library.getDescription()
        );

        library.setCreatedAt(libraryWithSheets.library.getCreatedAt());

        // Convert and add sheets
        if (libraryWithSheets.sheets != null) {
            List<SheetMusic> sheetMusicList = libraryWithSheets.sheets.stream()
                    .map(SheetMusicConverter::fromSheetEntity)
                    .collect(Collectors.toList());

            library.setSheets(sheetMusicList);
        }

        return library;
    }
}