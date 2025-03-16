package com.example.staffpad.data_model;

public class ToolItem {
    private String name;
    private int iconResId;

    public ToolItem(String name, int iconResId) {
        this.name = name;
        this.iconResId = iconResId;
    }

    public String getName() {
        return name;
    }

    public int getIconResId() {
        return iconResId;
    }
}