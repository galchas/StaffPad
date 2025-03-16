package com.example.staffpad.viewmodel;

import android.app.Application;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.staffpad.database.AudioEntity;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.repository.SheetRepository;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class SheetViewModel extends AndroidViewModel {

    private final SheetRepository repository;

    // LiveData for UI to observe
    private final LiveData<List<SheetEntity>> allSheets;
    private final MutableLiveData<Long> selectedSheetId = new MutableLiveData<>();
    private final LiveData<SheetEntity> selectedSheet;
    private final LiveData<List<AudioEntity>> sheetAudioFiles;

    public SheetViewModel(@NonNull Application application) {
        super(application);
        repository = new SheetRepository(application);
        allSheets = repository.getAllSheets();

        // Setup transformations for the selected sheet
        selectedSheet = Transformations.switchMap(selectedSheetId,
                repository::getSheetById);

        // Setup transformations for the selected sheet's audio files
        sheetAudioFiles = Transformations.switchMap(selectedSheetId,
                repository::getAudioFilesForSheet);
    }

    public LiveData<SheetEntity> getSheetById(long sheetId) {
        return repository.getSheetById(sheetId);
    }

    // Sheet operations
    public LiveData<List<SheetEntity>> getAllSheets() {
        return allSheets;
    }

    public LiveData<List<SheetEntity>> getRecentSheets(int limit) {
        return repository.getRecentSheets(limit);
    }

    public LiveData<List<SheetEntity>> searchSheets(String query) {
        return repository.searchSheets(query);
    }

    public LiveData<List<SheetEntity>> getSheetsByComposer(String composer) {
        return repository.getSheetsByComposer(composer);
    }

    public LiveData<List<SheetEntity>> getSheetsByGenre(String genre) {
        return repository.getSheetsByGenre(genre);
    }

    public LiveData<List<SheetEntity>> getSheetsByLabel(String label) {
        return repository.getSheetsByLabel(label);
    }

    public void insertSheet(SheetEntity sheet) {
        repository.insertSheet(sheet);
    }

    public void updateSheet(SheetEntity sheet) {
        repository.updateSheet(sheet);
    }

    public void deleteSheet(SheetEntity sheet) {
        repository.deleteSheet(sheet);
    }

    // Selected sheet operations
    public void selectSheet(long sheetId) {
        selectedSheetId.setValue(sheetId);
        repository.markSheetAsOpened(sheetId);
    }

    public LiveData<SheetEntity> getSelectedSheet() {
        return selectedSheet;
    }

    // Setlist operations for the selected sheet
    public void addSelectedSheetToSetlist(long setlistId) {
        Long sheetId = selectedSheetId.getValue();
        if (sheetId != null) {
            repository.addSheetToSetlist(sheetId, setlistId);
        }
    }

    public void removeSelectedSheetFromSetlist(long setlistId) {
        Long sheetId = selectedSheetId.getValue();
        if (sheetId != null) {
            repository.removeSheetFromSetlist(sheetId, setlistId);
        }
    }

    public LiveData<List<Integer>> getSetlistsForSelectedSheet() {
        return Transformations.switchMap(selectedSheetId,
                repository::getSetlistsForSheet);
    }


    // Audio operations for the selected sheet
    public LiveData<List<AudioEntity>> getAudioFilesForSelectedSheet() {
        return sheetAudioFiles;
    }

    public void addAudioToSelectedSheet(Uri audioUri) {
        Long sheetId = selectedSheetId.getValue();
        if (sheetId != null) {
            repository.addAudioToSheet(sheetId, audioUri);
        }
    }

    public void removeAudioFromSelectedSheet(long audioId) {
        repository.removeAudioFromSheet(audioId);
    }

    // Helper methods for getting data for auto-complete
    public LiveData<List<String>> getAllComposers() {
        return repository.getAllComposers();
    }

    public LiveData<List<String>> getAllGenres() {
        return repository.getAllGenres();
    }

    public LiveData<List<String>> getAllLabels() {
        return repository.getAllLabels();
    }

    // In SheetViewModel.java

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getApplication().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error getting filename from URI", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
    public void addSheet(Uri fileUri, String suggestedName, OnSheetAddedListener listener) {
        // Run in a background thread since we'll be doing file operations
        new Thread(() -> {
            try {
                Log.d("SheetViewModel", "Starting addSheet process");

                // Get file info
                String fileName = getFileNameFromUri(fileUri);
                Log.d("SheetViewModel", "File name: " + fileName);

                String extension = "";
                if (fileName != null && fileName.contains(".")) {
                    extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
                }
                Log.d("SheetViewModel", "File extension: " + extension);

                // Handle file based on type
                if ("pdf".equals(extension)) {
                    Log.d("SheetViewModel", "Handling PDF file");
                    handlePdfFile(fileUri, fileName, suggestedName, listener);
                } else if (isImageFile(extension)) {
                    Log.d("SheetViewModel", "Handling image file");
                    handleImageFile(fileUri, fileName, suggestedName, listener);
                } else {
                    Log.e("SheetViewModel", "Unsupported file type: " + extension);
                    runOnUiThread(() -> listener.onSheetAdded(-1, "Unsupported file type"));
                }
            } catch (Exception e) {
                Log.e("SheetViewModel", "Error in addSheet", e);
                runOnUiThread(() -> listener.onSheetAdded(-1, "Error: " + e.getMessage()));
            }
        }).start();
    }

    private void handlePdfFile(Uri fileUri, String fileName, String suggestedName, OnSheetAddedListener listener) {
        try {
            Log.d("SheetViewModel", "Copying PDF file");
            // Copy file to app's private storage
            File destFile = copyFileToAppStorage(fileUri, fileName);
            Log.d("SheetViewModel", "PDF copied to: " + destFile.getAbsolutePath());

            // Create sheet entity
            SheetEntity sheet = createSheetEntity(destFile, fileName, suggestedName);
            Log.d("SheetViewModel", "Created sheet entity: " + sheet.getTitle());

            // Insert into database
            long sheetId = repository.insertSheetSync(sheet);
            Log.d("SheetViewModel", "Sheet inserted with ID: " + sheetId);

            runOnUiThread(() -> listener.onSheetAdded(sheetId, null));
        } catch (Exception e) {
            Log.e("SheetViewModel", "Error handling PDF", e);
            runOnUiThread(() -> listener.onSheetAdded(-1, "Error: " + e.getMessage()));
        }
    }

    private void handleImageFile(Uri fileUri, String fileName, String suggestedName, OnSheetAddedListener listener) {
        try {
            Log.d("SheetViewModel", "Converting image to PDF");
            // Convert image to PDF
            String pdfFileName = (suggestedName != null ? suggestedName : removeExtension(fileName)) + ".pdf";
            File pdfFile = convertImageToPdf(fileUri, pdfFileName);
            Log.d("SheetViewModel", "Image converted to PDF: " + pdfFile.getAbsolutePath());

            // Create sheet entity
            SheetEntity sheet = createSheetEntity(pdfFile, pdfFileName, suggestedName);
            Log.d("SheetViewModel", "Created sheet entity: " + sheet.getTitle());

            // Insert into database
            long sheetId = repository.insertSheetSync(sheet);
            Log.d("SheetViewModel", "Sheet inserted with ID: " + sheetId);

            runOnUiThread(() -> listener.onSheetAdded(sheetId, null));
        } catch (Exception e) {
            Log.e("SheetViewModel", "Error handling image", e);
            runOnUiThread(() -> listener.onSheetAdded(-1, "Error: " + e.getMessage()));
        }
    }

    private File copyFileToAppStorage(Uri sourceUri, String fileName) throws IOException {
        Log.d("SheetViewModel", "Starting file copy");

        File destDir = new File(getApplication().getFilesDir(), "sheets");
        if (!destDir.exists()) {
            boolean created = destDir.mkdirs();
            Log.d("SheetViewModel", "Created directory: " + created);
        }

        File destFile = new File(destDir, fileName);

        try (InputStream in = getApplication().getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {

            if (in == null) {
                throw new IOException("Failed to open input stream");
            }

            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            Log.d("SheetViewModel", "File copied successfully");
        }

        return destFile;
    }

    private File convertImageToPdf(Uri imageUri, String outputFileName) throws IOException {
        Log.d("SheetViewModel", "Starting image to PDF conversion");

        File destDir = new File(getApplication().getFilesDir(), "sheets");
        if (!destDir.exists()) {
            boolean created = destDir.mkdirs();
            Log.d("SheetViewModel", "Created directory: " + created);
        }

        File outputFile = new File(destDir, outputFileName);

        // Load the image
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), imageUri);
        Log.d("SheetViewModel", "Loaded image: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // Create PDF document
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(bitmap.getWidth(), bitmap.getHeight()));
        document.addPage(page);

        // Convert bitmap to PDF
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        PDImageXObject image = LosslessFactory.createFromImage(document, bitmap);
        contentStream.drawImage(image, 0, 0);
        contentStream.close();

        // Save the PDF
        document.save(outputFile);
        document.close();
        Log.d("SheetViewModel", "PDF created successfully");

        return outputFile;
    }

    private SheetEntity createSheetEntity(File file, String fileName, String suggestedName) throws IOException {
        Log.d("SheetViewModel", "Creating sheet entity");

        // Get file size
        long fileSize = file.length();
        Log.d("SheetViewModel", "File size: " + fileSize);

        // Get page count from PDF
        PDDocument document = PDDocument.load(file);
        int pageCount = document.getNumberOfPages();
        document.close();
        Log.d("SheetViewModel", "Page count: " + pageCount);

        // Create entity
        String title = suggestedName != null ? suggestedName : removeExtension(fileName);
        SheetEntity sheet = new SheetEntity(title, fileName, file.getAbsolutePath(), fileSize, pageCount);
        Log.d("SheetViewModel", "Sheet entity created");

        return sheet;
    }

    private String removeExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    private boolean isImageFile(String extension) {
        return "jpg".equals(extension) || "jpeg".equals(extension) ||
                "png".equals(extension) || "gif".equals(extension) ||
                "bmp".equals(extension);
    }

    // Helper method to run code on UI thread
    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    // Callback interface
    public interface OnSheetAddedListener {
        void onSheetAdded(long sheetId, String errorMessage);
    }

}