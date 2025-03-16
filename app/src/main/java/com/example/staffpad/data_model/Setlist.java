package com.example.staffpad.data_model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Setlist implements Parcelable {
    public int id;
    public String name;
    public int itemCount;

    public Setlist(int id, String name, int itemCount) {
        this.id = id;
        this.name = name;
        this.itemCount = itemCount;
    }

    protected Setlist(Parcel in) {
        id = in.readInt();
        name = in.readString();
        itemCount = in.readInt();
    }

    public static final Creator<Setlist> CREATOR = new Creator<Setlist>() {
        @Override
        public Setlist createFromParcel(Parcel in) {
            return new Setlist(in);
        }

        @Override
        public Setlist[] newArray(int size) {
            return new Setlist[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeInt(itemCount);
    }
}
