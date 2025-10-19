package com.example.staffpad.database;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "sheets")
public class SheetEntity implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "filename")
    private String filename;

    @ColumnInfo(name = "file_path")
    private String filePath;

    @ColumnInfo(name = "file_size")
    private long fileSize;

    @ColumnInfo(name = "page_count")
    private int pageCount;

    @ColumnInfo(name = "composers")
    private String composers;

    @ColumnInfo(name = "genres")
    private String genres;

    @ColumnInfo(name = "labels")
    private String labels;

    @ColumnInfo(name = "references")
    private String references;

    @ColumnInfo(name = "rating")
    private float rating;

    @ColumnInfo(name = "key")
    private String key;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "last_opened_at")
    private long lastOpenedAt;

    // Optional JSON strings storing custom page order and deleted pages for this sheet
    @ColumnInfo(name = "page_order_json")
    private String pageOrderJson; // e.g., "[0,2,1,3]" mapping logical index -> original page

    @ColumnInfo(name = "deleted_pages_json")
    private String deletedPagesJson; // e.g., "[4,7]" original page indices deleted

    // Constructor
    public SheetEntity(String title, String filename, String filePath, long fileSize, int pageCount) {
        this.title = title;
        this.filename = filename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.key = "C Major"; // Default key
        this.createdAt = System.currentTimeMillis();
        this.lastOpenedAt = System.currentTimeMillis();
    }

    protected SheetEntity(Parcel in) {
        id = in.readLong();
        title = in.readString();
        filename = in.readString();
        filePath = in.readString();
        fileSize = in.readLong();
        pageCount = in.readInt();
        composers = in.readString();
        genres = in.readString();
        labels = in.readString();
        references = in.readString();
        rating = in.readFloat();
        key = in.readString();
        createdAt = in.readLong();
        lastOpenedAt = in.readLong();
        pageOrderJson = in.readString();
        deletedPagesJson = in.readString();
    }

    public static final Creator<SheetEntity> CREATOR = new Creator<SheetEntity>() {
        @Override
        public SheetEntity createFromParcel(Parcel in) {
            return new SheetEntity(in);
        }

        @Override
        public SheetEntity[] newArray(int size) {
            return new SheetEntity[size];
        }
    };

    public String getFormattedFileSize() {
        if (fileSize <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));
        return String.format("%.1f %s", fileSize / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public String getComposers() {
        return composers;
    }

    public void setComposers(String composers) {
        this.composers = composers;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getReferences() {
        return references;
    }

    public void setReferences(String references) {
        this.references = references;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(long lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public String getPageOrderJson() {
        return pageOrderJson;
    }

    public void setPageOrderJson(String pageOrderJson) {
        this.pageOrderJson = pageOrderJson;
    }

    public String getDeletedPagesJson() {
        return deletedPagesJson;
    }

    public void setDeletedPagesJson(String deletedPagesJson) {
        this.deletedPagesJson = deletedPagesJson;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(filename);
        dest.writeString(filePath);
        dest.writeLong(fileSize);
        dest.writeInt(pageCount);
        dest.writeString(composers);
        dest.writeString(genres);
        dest.writeString(labels);
        dest.writeString(references);
        dest.writeFloat(rating);
        dest.writeString(key);
        dest.writeLong(createdAt);
        dest.writeLong(lastOpenedAt);
        dest.writeString(pageOrderJson);
        dest.writeString(deletedPagesJson);
    }
}