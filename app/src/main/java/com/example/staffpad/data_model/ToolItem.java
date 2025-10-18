package com.example.staffpad.data_model;

public class ToolItem {
    private final String name;
    private final int iconResId;
    private final boolean header;

    // Constructor for a normal tool item
    public ToolItem(String name, int iconResId) {
        this.name = name;
        this.iconResId = iconResId;
        this.header = false;
    }

    // Constructor for a header item
    public ToolItem(String headerTitle) {
        this.name = headerTitle;
        this.iconResId = 0;
        this.header = true;
    }

    public String getName() {
        return name;
    }

    public int getIconResId() {
        return iconResId;
    }

    public boolean isHeader() {
        return header;
    }
}