package com.example.staffpad.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.staffpad.data_model.Library;
import com.example.staffpad.database.repository.LibraryRepository;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private final LibraryRepository repository;
    private final LiveData<List<Library>> allLibrariesWithSheets;

    public LibraryViewModel(Application application) {
        super(application);
        repository = new LibraryRepository(application);
        allLibrariesWithSheets = repository.getAllLibrariesWithSheets();
    }

    public LiveData<List<Library>> getAllLibrariesWithSheets() {
        return allLibrariesWithSheets;
    }

    public LiveData<Library> getLibraryWithSheets(long libraryId) {
        return repository.getLibraryWithSheets(libraryId);
    }

    // Other methods to manipulate libraries
}