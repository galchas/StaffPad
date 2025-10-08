package com.example.staffpad.data_model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.example.staffpad.database.SheetEntity;

import java.util.ArrayList;
import java.util.List;

public class SheetMusic implements Parcelable {
    public long id;
    private String title;
    private String filename;
    private long fileSize;
    private int pageCount;
    private String composers;
    private String genres;
    private String labels;
    private String references;
    private float rating;
    private String key;
    private String filePath;
    private List<Integer> setlists = new ArrayList<>();
    private List<AudioFile> audioFiles = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public SheetMusic(long id, String title, String filename, String filePath, long fileSize, int pageCount) {
        this.id = id;
        this.title = title;
        this.filename = filename;
        this.fileSize = fileSize;
        this.filePath = filePath; // ← Make sure this is set correctly!
        this.pageCount = pageCount;
        this.key = "C Major";  // Default key
    }

    public SheetMusic(SheetEntity sheet) {
        id = sheet.getId();
        title = sheet.getTitle();
        filename = sheet.getFilename();
        fileSize = sheet.getFileSize();
        pageCount = sheet.getPageCount();
        composers = sheet.getComposers();
        genres = sheet.getGenres();
        labels = sheet.getLabels();
        references = sheet.getReferences();
        rating = sheet.getRating();
        key = sheet.getKey();
        filePath = sheet.getFilePath();
    }


    public SheetMusic(Parcel in) {
        id = in.readLong();
        title = in.readString();
        filename = in.readString();
        fileSize = in.readLong();
        pageCount = in.readInt();
        composers = in.readString();
        genres = in.readString();
        labels = in.readString();
        references = in.readString();
        rating = in.readFloat();
        key = in.readString();
        filePath = in.readString();
    }

    public static final Creator<SheetMusic> CREATOR = new Creator<>() {
        @Override
        public SheetMusic createFromParcel(Parcel in) {
            return new SheetMusic(in);
        }

        @Override
        public SheetMusic[] newArray(int size) {
            return new SheetMusic[size];
        }
    };

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilename() {
        return filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getFormattedFileSize() {
        if (fileSize <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));
        return String.format("%.1f %s", fileSize / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public int getPageCount() {
        return pageCount;
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

    public List<Integer> getSetlists() {
        return setlists;
    }

    public void setSetlists(List<Integer> setlists) {
        this.setlists = setlists;
    }

    public List<AudioFile> getAudioFiles() {
        return audioFiles;
    }

    public void addAudioFile(AudioFile audioFile) {
        this.audioFiles.add(audioFile);
    }

    public void removeAudioFile(AudioFile audioFile) {
        this.audioFiles.remove(audioFile);
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
        dest.writeLong(fileSize);
        dest.writeInt(pageCount);
        dest.writeString(composers);
        dest.writeString(genres);
        dest.writeString(labels);
        dest.writeString(references);
        dest.writeFloat(rating);
        dest.writeString(key);
        dest.writeString(filePath);
    }

    public String getFilePath() {
        return filePath;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}