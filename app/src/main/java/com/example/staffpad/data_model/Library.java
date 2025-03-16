package com.example.staffpad.data_model;

import java.util.ArrayList;
import java.util.List;

public class Library {
    private long id;
    private String name;
    private String description;
    private long createdAt;
    private List<SheetMusic> sheets = new ArrayList<>();

    public Library(long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<SheetMusic> getSheets() {
        return sheets;
    }

    public void setSheets(List<SheetMusic> sheets) {
        this.sheets = sheets;
    }

    public void addSheet(SheetMusic sheet) {
        if (!sheets.contains(sheet)) {
            sheets.add(sheet);
        }
    }

    public void removeSheet(SheetMusic sheet) {
        sheets.remove(sheet);
    }
}