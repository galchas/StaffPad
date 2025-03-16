package com.example.staffpad.data_model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class AudioFile implements Parcelable {
    private long id;
    private String name;
    private long size;
    private String duration;
    private String uri;

    public AudioFile(String name, long size, String duration, String uri) {
        this.name = name;
        this.size = size;
        this.duration = duration;
        this.uri = uri;
    }

    protected AudioFile(Parcel in) {
        id = in.readLong();
        name = in.readString();
        size = in.readLong();
        duration = in.readString();
        uri = in.readString();
    }

    public static final Creator<AudioFile> CREATOR = new Creator<>() {
        @Override
        public AudioFile createFromParcel(Parcel in) {
            return new AudioFile(in);
        }

        @Override
        public AudioFile[] newArray(int size) {
            return new AudioFile[size];
        }
    };

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getDuration() {
        return duration;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeLong(size);
        dest.writeString(duration);
        dest.writeString(uri);
    }
}
