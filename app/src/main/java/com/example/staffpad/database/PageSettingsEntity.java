package com.example.staffpad.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "page_settings",
        foreignKeys = @ForeignKey(
                entity = SheetEntity.class,
                parentColumns = "id",
                childColumns = "sheet_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("sheet_id"),
                @Index(value = {"sheet_id", "page_number"}, unique = true)
        }
)
public class PageSettingsEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "sheet_id")
    private long sheetId;

    @ColumnInfo(name = "page_number")
    private int pageNumber;

    // Normalized crop (0..1 relative to original page bitmap)
    @ColumnInfo(name = "crop_left")
    private float cropLeft;

    @ColumnInfo(name = "crop_top")
    private float cropTop;

    @ColumnInfo(name = "crop_right")
    private float cropRight;

    @ColumnInfo(name = "crop_bottom")
    private float cropBottom;

    @ColumnInfo(name = "rotation")
    private float rotation; // degrees

    @ColumnInfo(name = "brightness")
    private float brightness; // -100..100

    @ColumnInfo(name = "contrast")
    private float contrast; // 0.5..2.0

    @ColumnInfo(name = "modified_at")
    private long modifiedAt;

    public PageSettingsEntity(long sheetId, int pageNumber) {
        this.sheetId = sheetId;
        this.pageNumber = pageNumber;
        // Defaults: full image, no rotation, default adjustments
        this.cropLeft = 0f;
        this.cropTop = 0f;
        this.cropRight = 1f;
        this.cropBottom = 1f;
        this.rotation = 0f;
        this.brightness = 0f;
        this.contrast = 1f;
        this.modifiedAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSheetId() { return sheetId; }
    public void setSheetId(long sheetId) { this.sheetId = sheetId; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public float getCropLeft() { return cropLeft; }
    public void setCropLeft(float cropLeft) { this.cropLeft = cropLeft; }

    public float getCropTop() { return cropTop; }
    public void setCropTop(float cropTop) { this.cropTop = cropTop; }

    public float getCropRight() { return cropRight; }
    public void setCropRight(float cropRight) { this.cropRight = cropRight; }

    public float getCropBottom() { return cropBottom; }
    public void setCropBottom(float cropBottom) { this.cropBottom = cropBottom; }

    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation; }

    public float getBrightness() { return brightness; }
    public void setBrightness(float brightness) { this.brightness = brightness; }

    public float getContrast() { return contrast; }
    public void setContrast(float contrast) { this.contrast = contrast; }

    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }

    // Helpers
    public boolean hasCrop() {
        return !(approxEqual(cropLeft, 0f) && approxEqual(cropTop, 0f)
                && approxEqual(cropRight, 1f) && approxEqual(cropBottom, 1f));
    }

    public boolean hasRotation() { return !approxEqual(rotation, 0f); }
    public boolean hasAdjustments() { return !approxEqual(brightness, 0f) || !approxEqual(contrast, 1f); }

    private boolean approxEqual(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }
}
