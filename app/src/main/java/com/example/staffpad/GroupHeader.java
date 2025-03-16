package com.example.staffpad;

import com.example.staffpad.data_model.SheetMusic;

import java.util.List;

public class GroupHeader {
    private String title;
    private List<SheetMusic> sheets;

    public GroupHeader(String title, List<SheetMusic> sheets) {
        this.title = title;
        this.sheets = sheets;
    }

    public String getTitle() {
        return title;
    }

    public List<SheetMusic> getSheets() {
        return sheets;
    }

    public int getCount() {
        return sheets.size();
    }
}