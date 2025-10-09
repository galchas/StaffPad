package com.example.staffpad.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Represents a layer on a specific page of a sheet.
 * Layers can contain crops, rotations, annotations, etc.
 */
@Entity(
        tableName = "page_layers",
        foreignKeys = {
                @ForeignKey(
                        entity = SheetEntity.class,
                        parentColumns = "id",
                        childColumns = "sheet_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("sheet_id"),
                @Index(value = {"sheet_id", "page_number"})
        }
)
public class PageLayerEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "sheet_id")
    private long sheetId;

    @ColumnInfo(name = "page_number")
    private int pageNumber;

    @ColumnInfo(name = "layer_name")
    private String layerName;

    @ColumnInfo(name = "layer_type")
    private String layerType; // "CROP", "ANNOTATION", "COMBINED"

    @ColumnInfo(name = "is_active")
    private boolean isActive; // Whether this layer is currently visible

    @ColumnInfo(name = "order_index")
    private int orderIndex; // Layer stacking order

    // Crop data
    @ColumnInfo(name = "crop_left")
    private float cropLeft;

    @ColumnInfo(name = "crop_top")
    private float cropTop;

    @ColumnInfo(name = "crop_right")
    private float cropRight;

    @ColumnInfo(name = "crop_bottom")
    private float cropBottom;

    @ColumnInfo(name = "rotation")
    private float rotation; // in degrees

    // Image adjustment data
    @ColumnInfo(name = "brightness")
    private float brightness;

    @ColumnInfo(name = "contrast")
    private float contrast;

    // Optional: path to rendered layer image
    @ColumnInfo(name = "layer_image_path")
    private String layerImagePath;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "modified_at")
    private long modifiedAt;

    public PageLayerEntity(long sheetId, int pageNumber, String layerName, String layerType) {
        this.sheetId = sheetId;
        this.pageNumber = pageNumber;
        this.layerName = layerName;
        this.layerType = layerType;
        this.isActive = true;
        this.orderIndex = 0;
        this.rotation = 0;
        this.brightness = 0;
        this.contrast = 1.0f;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSheetId() {
        return sheetId;
    }

    public void setSheetId(long sheetId) {
        this.sheetId = sheetId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public String getLayerType() {
        return layerType;
    }

    public void setLayerType(String layerType) {
        this.layerType = layerType;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public float getCropLeft() {
        return cropLeft;
    }

    public void setCropLeft(float cropLeft) {
        this.cropLeft = cropLeft;
    }

    public float getCropTop() {
        return cropTop;
    }

    public void setCropTop(float cropTop) {
        this.cropTop = cropTop;
    }

    public float getCropRight() {
        return cropRight;
    }

    public void setCropRight(float cropRight) {
        this.cropRight = cropRight;
    }

    public float getCropBottom() {
        return cropBottom;
    }

    public void setCropBottom(float cropBottom) {
        this.cropBottom = cropBottom;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    public String getLayerImagePath() {
        return layerImagePath;
    }

    public void setLayerImagePath(String layerImagePath) {
        this.layerImagePath = layerImagePath;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public boolean hasCrop() {
        return cropLeft != 0 || cropTop != 0 || cropRight != 0 || cropBottom != 0;
    }

    public boolean hasRotation() {
        return rotation != 0;
    }

    public boolean hasAdjustments() {
        return brightness != 0 || contrast != 1.0f;
    }
}