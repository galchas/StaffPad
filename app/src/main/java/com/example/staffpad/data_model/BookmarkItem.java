package com.example.staffpad.data_model;

public class BookmarkItem {
    private final String name;
    private final int page;

    public BookmarkItem(String name, int page) {
        this.name = name;
        this.page = page;
    }

    public String getName() {
        return name;
    }

    public int getPage() {
        return page;
    }
}