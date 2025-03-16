package com.example.staffpad.data_model;

import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.SheetWithRelations;
import com.example.staffpad.database.AudioEntity;
import com.example.staffpad.database.TagEntity;

import java.util.List;
import java.util.stream.Collectors;

public class SheetMusicConverter {

    public static SheetMusic fromSheetWithRelations(SheetWithRelations sheetWithRelations) {
        // Create SheetMusic object with basic data
        SheetMusic sheetMusic = new SheetMusic(
                sheetWithRelations.sheet.getId(),
                sheetWithRelations.sheet.getTitle(),
                sheetWithRelations.sheet.getFilename(),
                sheetWithRelations.sheet.getFilePath(),
                sheetWithRelations.sheet.getFileSize(),
                sheetWithRelations.sheet.getPageCount()
        );

        // Set the ID and other properties
        sheetMusic.setId(sheetWithRelations.sheet.getId());
        sheetMusic.setComposers(sheetWithRelations.sheet.getComposers());
        sheetMusic.setGenres(sheetWithRelations.sheet.getGenres());
        sheetMusic.setLabels(sheetWithRelations.sheet.getLabels());
        sheetMusic.setFilePath(sheetWithRelations.sheet.getFilePath());
        sheetMusic.setReferences(sheetWithRelations.sheet.getReferences());
        sheetMusic.setRating(sheetWithRelations.sheet.getRating());
        sheetMusic.setKey(sheetWithRelations.sheet.getKey());

        // Convert and add audio files
        if (sheetWithRelations.audioFiles != null) {
            for (AudioEntity audioEntity : sheetWithRelations.audioFiles) {
                AudioFile audioFile = new AudioFile(
                        audioEntity.getName(),
                        audioEntity.getSize(),
                        audioEntity.getDurationString(),
                        audioEntity.getUri()
                );
                audioFile.setId(audioEntity.getId());
                sheetMusic.addAudioFile(audioFile);
            }
        }

        // Add setlist IDs
        if (sheetWithRelations.setlists != null) {
            List<Integer> setlistIds = sheetWithRelations.setlists.stream()
                    .map(setlist -> (int) setlist.getId())
                    .collect(Collectors.toList());
            sheetMusic.setSetlists(setlistIds);
        }

        // Add tags
        if (sheetWithRelations.tags != null) {
            List<String> tags = sheetWithRelations.tags.stream()
                    .map(TagEntity::getName)
                    .collect(Collectors.toList());
            sheetMusic.setTags(tags);
        }

        return sheetMusic;
    }

    public static SheetMusic fromSheetEntity(SheetEntity entity) {
        SheetMusic sheetMusic = new SheetMusic(
                entity.getId(),
                entity.getTitle(),
                entity.getFilename(),
                entity.getFilePath(),
                entity.getFileSize(),
                entity.getPageCount()
        );

        sheetMusic.setId(entity.getId());
        sheetMusic.setComposers(entity.getComposers());
        sheetMusic.setGenres(entity.getGenres());
        sheetMusic.setLabels(entity.getLabels());
        sheetMusic.setReferences(entity.getReferences());
        sheetMusic.setRating(entity.getRating());
        sheetMusic.setKey(entity.getKey());

        // Note: This doesn't fill in related entities like audioFiles, tags, etc.
        // Those would need to be loaded separately or through a join query

        return sheetMusic;
    }
}