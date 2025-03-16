package com.example.staffpad.data_model;

public class SheetMusicItem  {
    private final String title;
    private final String composer;

    public SheetMusicItem(String title, String composer) {
        this.title = title;
        this.composer = composer;
    }

    public String getTitle() {
        return title;
    }

    public String getComposer() {
        return composer;
    }
}