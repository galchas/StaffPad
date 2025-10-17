package com.example.staffpad.database.repository;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.AudioDao;
import com.example.staffpad.database.AudioEntity;
import com.example.staffpad.database.SetlistDao;
import com.example.staffpad.database.SheetDao;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.SheetSetlistCrossRef;
import com.example.staffpad.database.SheetWithAudioFiles;
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
import java.util.concurrent.ExecutorService;

public class SheetRepository {
    private final SheetDao sheetDao;
    private final SetlistDao setlistDao;
    private final AudioDao audioDao;
    private final ExecutorService executorService;
    private final Application application;

    public SheetRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        this.sheetDao = db.sheetDao();
        this.setlistDao = db.setlistDao();
        this.audioDao = db.audioDao();
        this.executorService = AppDatabase.databaseWriteExecutor;
        this.application = application;
    }

    // Sheet CRUD operations
    public void insertSheet(SheetEntity sheet) {
        executorService.execute(() -> {
            long sheetId = sheetDao.insertSheet(sheet);
        });
    }

    public void updateSheet(SheetEntity sheet) {
        executorService.execute(() -> {
            sheetDao.updateSheet(sheet);
        });
    }

    public void deleteSheet(SheetEntity sheet) {
        executorService.execute(() -> {
            sheetDao.deleteSheet(sheet);
        });
    }

    public LiveData<List<SheetEntity>> getAllSheets() {
        return sheetDao.getAllSheets();
    }

    public LiveData<SheetEntity> getSheetById(long id) {
        return sheetDao.getSheetById(id);
    }

    public LiveData<List<SheetEntity>> getRecentSheets(int limit) {
        return sheetDao.getRecentSheets(limit);
    }

    public LiveData<List<SheetEntity>> searchSheets(String query) {
        return sheetDao.searchSheets(query);
    }

    public LiveData<List<SheetEntity>> getSheetsByComposer(String composer) {
        return sheetDao.getSheetsByComposer(composer);
    }

    public LiveData<List<SheetEntity>> getSheetsByGenre(String genre) {
        return sheetDao.getSheetsByGenre(genre);
    }

    public LiveData<List<SheetEntity>> getSheetsByLabel(String label) {
        return sheetDao.getSheetsByLabel(label);
    }

    public LiveData<List<String>> getAllComposers() {
        return sheetDao.getAllComposers();
    }

    public LiveData<List<String>> getAllGenres() {
        return sheetDao.getAllGenres();
    }

    public LiveData<List<String>> getAllLabels() {
        return sheetDao.getAllLabels();
    }

    public void markSheetAsOpened(long sheetId) {
        executorService.execute(() -> {
            sheetDao.updateLastOpenedAt(sheetId, System.currentTimeMillis());
        });
    }

    // Setlist operations
    public void addSheetToSetlist(long sheetId, long setlistId) {
        executorService.execute(() -> {
            int position = setlistDao.getNextPositionInSetlist(setlistId);
            SheetSetlistCrossRef crossRef = new SheetSetlistCrossRef(sheetId, setlistId, position);
            setlistDao.insertSheetSetlistCrossRef(crossRef);
            setlistDao.updateModifiedAt(setlistId, System.currentTimeMillis());
        });
    }

    public void removeSheetFromSetlist(long sheetId, long setlistId) {
        executorService.execute(() -> {
            setlistDao.removeSheetFromSetlist(sheetId, setlistId);
            setlistDao.updateModifiedAt(setlistId, System.currentTimeMillis());
        });
    }

    public LiveData<List<Integer>> getSetlistsForSheet(long sheetId) {
        return setlistDao.getSetlistsForSheet(sheetId);
    }

    public boolean isSheetInSetlist(long sheetId, long setlistId) {
        return setlistDao.isSheetInSetlist(sheetId, setlistId);
    }

    public LiveData<List<SheetEntity>> getSheetsInSetlist(long setlistId) {
        return sheetDao.getSheetsInSetlist(setlistId);
    }

    public void updateSheetPositionInSetlist(long sheetId, long setlistId, long newPosition) {
        executorService.execute(() -> {
            setlistDao.updateSheetPositionInSetlist(sheetId, setlistId, newPosition);
            setlistDao.updateModifiedAt(setlistId, System.currentTimeMillis());
        });
    }

    // Audio file operations
    public void addAudioToSheet(long sheetId, Uri audioUri) {
        executorService.execute(() -> {
            try {
                // Get audio file info
                String filename = getFileNameFromUri(audioUri);
                String filePath = copyAudioFileToAppStorage(audioUri, filename);
                long fileSize = new File(filePath).length();

                // Extract duration using MediaMetadataRetriever on the copied file
                long duration = 0L;
                String durationFormatted = "0:00";
                try {
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    mmr.setDataSource(filePath);
                    String durStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durStr != null) {
                        duration = Long.parseLong(durStr);
                        long m = (duration / 1000) / 60;
                        long s = (duration / 1000) % 60;
                        durationFormatted = m + ":" + (s < 10 ? ("0" + s) : String.valueOf(s));
                    }
                    mmr.release();
                } catch (Throwable t) {
                    Log.w("SheetRepository", "Failed to read audio duration: " + filePath, t);
                }

                // Create and insert audio entity with real filename and duration
                AudioEntity audioEntity = new AudioEntity(
                        sheetId,
                        filename,
                        filePath,
                        audioUri.toString(),
                        fileSize,
                        duration,
                        durationFormatted
                );

                audioDao.insertAudio(audioEntity);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void removeAudioFromSheet(long audioId) {
        executorService.execute(() -> {
            try {
                AudioEntity audio = audioDao.getAudioByIdSync(audioId);
                if (audio != null) {
                    String path = audio.getFilePath();
                    if (path != null && !path.trim().isEmpty()) {
                        try {
                            File f = new File(path);
                            if (f.exists()) {
                                boolean deleted = f.delete();
                                Log.d("SheetRepository", "Deleted audio file " + path + ": " + deleted);
                            }
                        } catch (Throwable t) {
                            Log.w("SheetRepository", "Failed to delete audio file: " + path, t);
                        }
                    }
                }
                audioDao.deleteById(audioId);
            } catch (Throwable t) {
                Log.e("SheetRepository", "removeAudioFromSheet failed", t);
            }
        });
    }

    public void addAudioLinkToSheet(long sheetId, String url) {
        executorService.execute(() -> {
            try {
                if (url == null) return;
                String u = url.trim();
                if (u.isEmpty()) return;
                String low = u.toLowerCase();
                boolean yt = low.contains("youtube.com/watch") || low.contains("youtu.be/");
                if (!yt) {
                    Log.w("SheetRepository", "Rejected non-YouTube URL: " + url);
                    return;
                }
                int index = 1;
                try { index = audioDao.countYouTubeLinksForSheet(sheetId) + 1; } catch (Throwable ignore) {}
                String sheetName = "Sheet";
                try {
                    SheetEntity s = sheetDao.getSheetByIdSync(sheetId);
                    if (s != null && s.getTitle() != null && !s.getTitle().trim().isEmpty()) sheetName = s.getTitle().trim();
                } catch (Throwable ignore) {}
                String safeName = sheetName.replaceAll("[\\\\/:*?\"<>|]", "_");
                String displayName = safeName + "_Clip_" + index;
                AudioEntity audio = new AudioEntity(sheetId, displayName, null, u, 0L, 0L, "0:00");
                audioDao.insertAudio(audio);
            } catch (Throwable t) {
                Log.e("SheetRepository", "addAudioLinkToSheet failed", t);
            }
        });
    }

    public LiveData<List<AudioEntity>> getAudioFilesForSheet(long sheetId) {
        return audioDao.getAudioFilesForSheet(sheetId);
    }

    public LiveData<SheetWithAudioFiles> getSheetWithAudioFiles(long sheetId) {
        // This would require adding a method to SheetDao
        return null; // Placeholder
    }

    // Helper methods for file operations
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                android.database.Cursor cursor = application.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1) {
                                result = cursor.getString(nameIndex);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("SheetRepository", "DISPLAY_NAME query failed for uri: " + uri, t);
        }
        if (result == null || result.trim().isEmpty()) {
            result = uri.getLastPathSegment();
            if (result != null && result.contains("/")) {
                result = result.substring(result.lastIndexOf('/') + 1);
            }
        }
        return result != null ? result : "unknown_file";
    }

    private String copyAudioFileToAppStorage(Uri sourceUri, String filename) throws IOException {
        File destDir = new File(application.getFilesDir(), "audio");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        File destFile = new File(destDir, filename);

        try (InputStream in = application.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {

            if (in == null) {
                throw new IOException("Failed to open input stream");
            }

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }

        return destFile.getAbsolutePath();
    }
        // Add this method to your SheetRepository class
        public long insertSheetSync(SheetEntity sheet) {
            // Use a Callable to get the result from the background thread
            try {
                return AppDatabase.databaseWriteExecutor.submit(() -> sheetDao.insertSheet(sheet)).get();
            } catch (Exception e) {
                Log.e("SheetRepository", "Error inserting sheet synchronously", e);
                return -1;
            }
        }
        public void addSheet(Uri fileUri, String suggestedName, OnSheetAddedCallback callback) {
            executorService.execute(() -> {
                long sheetId = -1;
                try {
                    // Get file name and extension
                    String originalFileName = getFileNameFromUri(fileUri);
                    String extension = getFileExtension(originalFileName).toLowerCase();

                    // Handle file based on its type
                    if (extension.equals("pdf")) {
                        // It's a PDF - just copy it
                        String filePath = copyFileToAppStorage(fileUri, originalFileName, "sheets");
                        // Create sheet entity
                        SheetEntity sheet = createSheetEntityFromPdf(filePath, originalFileName, suggestedName);
                        sheetId = sheetDao.insertSheet(sheet);
                    } else if (isImageFile(extension)) {
                        // It's an image - convert to PDF
                        String pdfFileName = (suggestedName != null ? suggestedName : removeExtension(originalFileName)) + ".pdf";
                        String pdfPath = convertImageToPdf(fileUri, pdfFileName);
                        // Create sheet entity
                        SheetEntity sheet = createSheetEntityFromPdf(pdfPath, pdfFileName, suggestedName);
                        sheetId = sheetDao.insertSheet(sheet);
                    }

                    // Use Handler to call back on main thread
                    final long finalSheetId = sheetId;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onSheetAdded(finalSheetId);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    // Call back with error
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onSheetAdded(-1);
                    });
                }
            });
        }

    // Callback interface
    public interface OnSheetAddedCallback {
        void onSheetAdded(long sheetId);
    }

        /**
         * Creates a SheetEntity from a PDF file
         */
        private SheetEntity createSheetEntityFromPdf(String filePath, String fileName, String suggestedName) throws IOException {
            File pdfFile = new File(filePath);
            long fileSize = pdfFile.length();

            // Open PDF to get page count
            PDDocument document = PDDocument.load(pdfFile);
            int pageCount = document.getNumberOfPages();
            document.close();

            // Create the sheet entity
            String title = suggestedName != null ? suggestedName : removeExtension(fileName);
            SheetEntity sheet = new SheetEntity(title, fileName, filePath, fileSize, pageCount);

            return sheet;
        }

        /**
         * Convert an image file to PDF
         */
        private String convertImageToPdf(Uri imageUri, String outputFileName) throws IOException {
            // Create Documents/StaffPad directory if it doesn't exist
            File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "StaffPad");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Create output file
            File outputFile = new File(outputDir, outputFileName);

            // Load the image
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(application.getContentResolver(), imageUri);

            // Create a new PDF document
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

            return outputFile.getAbsolutePath();
        }

        /**
         * Check if the extension is an image file
         */
        private boolean isImageFile(String extension) {
            return extension.equals("jpg") || extension.equals("jpeg") ||
                    extension.equals("png") || extension.equals("gif") ||
                    extension.equals("bmp");
        }

        /**
         * Get file extension from name
         */
        private String getFileExtension(String fileName) {
            if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
                return fileName.substring(fileName.lastIndexOf(".") + 1);
            } else {
                return "";
            }
        }

        /**
         * Remove extension from file name
         */
        private String removeExtension(String fileName) {
            if (fileName.lastIndexOf(".") != -1) {
                return fileName.substring(0, fileName.lastIndexOf("."));
            } else {
                return fileName;
            }
        }

        /**
         * Copy file to app storage
         */
        private String copyFileToAppStorage(Uri sourceUri, String fileName, String subdirectory) throws IOException {
            File destDir = new File(application.getFilesDir(), subdirectory);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File destFile = new File(destDir, fileName);

            try (InputStream in = application.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(destFile)) {

                if (in == null) {
                    throw new IOException("Failed to open input stream");
                }

                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }

            return destFile.getAbsolutePath();
        }
}
