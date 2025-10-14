package com.example.staffpad;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import com.google.android.material.slider.Slider;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staffpad.views.CropImageView;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.PageLayerDao;
import com.example.staffpad.database.PageLayerEntity;
import com.example.staffpad.database.PageSettingsDao;
import com.example.staffpad.database.PageSettingsEntity;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;

import java.io.File;
import java.io.IOException;

public class CropActivity extends AppCompatActivity {
    private static final String TAG = "CropActivity";

    public static final String EXTRA_SHEET_ID = "sheet_id";
    public static final String EXTRA_PAGE_NUMBER = "page_number";
    public static final String EXTRA_FILE_PATH = "file_path";

    private CropImageView cropImageView;
    private ImageButton rotateLeftButton;
    private ImageButton rotateRightButton;
    private ImageButton resetButton;
    private Button applyButton;
    private Button cancelButton;
    private Button restoreButton;
    private Slider brightnessSlider;
    private Slider contrastSlider;
    private Slider rotationSlider;
    private TextView brightnessLabel;
    private TextView contrastLabel;
    private TextView rotationLabel;

    private boolean userHasChanged = false;
    private boolean suppressNextCropEvent = true;

    private long sheetId;
    private int pageNumber;
    private String filePath;
    private Bitmap originalBitmap;
    private PDDocument document;
    private float currentRotation = 0f;
    private float brightness = 0f; // -100 to 100
    private float contrast = 1f; // 0.5 to 2.0

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_crop);

        // Get extras
        sheetId = getIntent().getLongExtra(EXTRA_SHEET_ID, -1);
        pageNumber = getIntent().getIntExtra(EXTRA_PAGE_NUMBER, 0);
        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);

        if (sheetId == -1 || filePath == null) {
            Toast.makeText(this, "Error: Invalid sheet data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupListeners();
        loadPage();
        // Per UX (B): show original page with default controls; do not pre-apply saved settings.
        // We will enable the Apply button only when the user makes changes.
    }

    private void initializeViews() {
        cropImageView = findViewById(R.id.crop_image_view);
        rotateLeftButton = findViewById(R.id.rotate_left_button);
        rotateRightButton = findViewById(R.id.rotate_right_button);
        resetButton = findViewById(R.id.reset_button);
        applyButton = findViewById(R.id.apply_button);
        cancelButton = findViewById(R.id.cancel_button);
        brightnessSlider = findViewById(R.id.brightness_slider);
        contrastSlider = findViewById(R.id.contrast_slider);
        rotationSlider = findViewById(R.id.rotation_slider);
        brightnessLabel = findViewById(R.id.brightness_label);
        contrastLabel = findViewById(R.id.contrast_label);
        rotationLabel = findViewById(R.id.rotation_label);
        restoreButton = findViewById(R.id.restore_button);

        // Setup sliders initial values
        brightnessSlider.setValue(0f); // -100..100
        contrastSlider.setValue(1.0f); // 0.5..2.0
        rotationSlider.setValue(0f); // -10..10
    }

    private void setupListeners() {
        applyButton.setEnabled(false);

        rotateLeftButton.setOnClickListener(v -> {
            rotateImage(-90);
            markChanged();
        });
        rotateRightButton.setOnClickListener(v -> {
            rotateImage(90);
            markChanged();
        });
        resetButton.setOnClickListener(v -> {
            resetImage();
            // After reset to defaults, no changes pending
            userHasChanged = false;
            updateApplyEnabled();
        });
        applyButton.setOnClickListener(v -> applyChanges());
        cancelButton.setOnClickListener(v -> finish());
        if (restoreButton != null) {
            restoreButton.setOnClickListener(v -> restoreLastSnapshot());
        }

        // Track crop rectangle changes from user drags
        cropImageView.setOnCropChangeListener(newRect -> {
            if (suppressNextCropEvent) {
                suppressNextCropEvent = false;
                return;
            }
            markChanged();
        });

        // Fine rotation: slider value is -10..+10 degrees
        rotationSlider.addOnChangeListener((slider, value, fromUser) -> {
            currentRotation = value;
            rotationLabel.setText(String.format("Align: %.1f°", currentRotation));
            cropImageView.setRotation(currentRotation);
            if (fromUser) markChanged();
        });

        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            brightness = value; // already -100..100
            brightnessLabel.setText(String.format("Brightness: %d", (int) brightness));
            applyFilters();
            if (fromUser) markChanged();
        });

        contrastSlider.addOnChangeListener((slider, value, fromUser) -> {
            contrast = value; // already 0.5..2.0
            contrastLabel.setText(String.format("Contrast: %.2f", contrast));
            applyFilters();
            if (fromUser) markChanged();
        });
    }

    private void markChanged() {
        if (!userHasChanged) {
            userHasChanged = true;
            updateApplyEnabled();
        }
    }

    private void updateApplyEnabled() {
        if (applyButton != null) applyButton.setEnabled(userHasChanged);
    }

    private void loadPage() {
        new Thread(() -> {
            try {
                File pdfFile = new File(filePath);
                if (!pdfFile.exists()) {
                    runOnUiThreadSafe(() -> {
                        Toast.makeText(this, "Error: PDF file not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                document = PDDocument.load(pdfFile);
                PDFRenderer renderer = new PDFRenderer(document);

                if (pageNumber >= document.getNumberOfPages()) {
                    pageNumber = 0;
                }

                // Render at high resolution for better quality with fallback
                try {
                    originalBitmap = renderer.renderImage(pageNumber, 2.0f);
                } catch (Throwable t) {
                    Log.e(TAG, "PdfBox render failed in CropActivity, using Android PdfRenderer", t);
                    originalBitmap = renderWithAndroidPdfRenderer(new File(filePath), pageNumber, 2.0f);
                }

                runOnUiThreadSafe(() -> {
                    if (originalBitmap != null) {
                        cropImageView.setImageBitmap(originalBitmap);
                        Log.d(TAG, "Page loaded successfully");
                        // After the bitmap is set, optionally apply saved settings to the UI/preview
                        AppDatabase adb = AppDatabase.getDatabase(getApplicationContext());
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            PageSettingsDao sdao = adb.pageSettingsDao();
                            PageSettingsEntity settings = sdao.getByPage(sheetId, pageNumber);
                            if (settings != null) {
                                runOnUiThreadSafe(() -> {
                                    // Prefill controls and preview to reflect current saved settings
                                    currentRotation = settings.getRotation();
                                    brightness = settings.getBrightness();
                                    contrast = settings.getContrast();
                                    rotationSlider.setValue(currentRotation);
                                    rotationLabel.setText(String.format("Align: %.1f°", currentRotation));
                                    cropImageView.setRotation(currentRotation);
                                    brightnessSlider.setValue(brightness);
                                    brightnessLabel.setText(String.format("Brightness: %d", (int) brightness));
                                    contrastSlider.setValue(contrast);
                                    contrastLabel.setText(String.format("Contrast: %.2f", contrast));
                                    // Set crop rectangle if not full image
                                    android.graphics.RectF nb = new android.graphics.RectF(
                                            settings.getCropLeft(), settings.getCropTop(),
                                            settings.getCropRight(), settings.getCropBottom());
                                    if (!(Math.abs(nb.left) < 1e-3 && Math.abs(nb.top) < 1e-3 && Math.abs(nb.right - 1f) < 1e-3 && Math.abs(nb.bottom - 1f) < 1e-3)) {
                                        cropImageView.setCropFromNormalized(nb);
                                    }
                                });
                            }
                        });
                    } else {
                        Toast.makeText(this, "Error loading page", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading PDF", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void rotateImage(float degrees) {
        // Nudge rotation slightly within -10..+10 degrees range
        currentRotation += degrees;
        if (currentRotation > 10f) currentRotation = 10f;
        if (currentRotation < -10f) currentRotation = -10f;
        cropImageView.setRotation(currentRotation);
        // Sync UI slider and label if available
        if (rotationSlider != null) {
            rotationSlider.setValue(currentRotation);
        }
        if (rotationLabel != null) {
            rotationLabel.setText(String.format("Align: %.1f°", currentRotation));
        }
        Log.d(TAG, "Rotated to: " + currentRotation);
    }

    private void resetImage() {
        currentRotation = 0f;
        brightness = 0f;
        contrast = 1f;

        cropImageView.setRotation(0);
        cropImageView.resetCrop();
        if (brightnessSlider != null) brightnessSlider.setValue(0f);
        if (contrastSlider != null) contrastSlider.setValue(1.0f);
        if (rotationSlider != null) rotationSlider.setValue(0f);
        if (rotationLabel != null) rotationLabel.setText("Align: 0°");

        if (originalBitmap != null) {
            // Show original with no filters
            cropImageView.setImageBitmap(originalBitmap);
        }

        Toast.makeText(this, "Reset to original", Toast.LENGTH_SHORT).show();
    }

    private void applyFilters() {
        if (originalBitmap == null) return;

        new Thread(() -> {
            Bitmap filteredBitmap = adjustBrightnessContrast(originalBitmap, brightness, contrast);
            runOnUiThreadSafe(() -> cropImageView.setImageBitmap(filteredBitmap));
        }).start();
    }

    private Bitmap adjustBrightnessContrast(Bitmap bitmap, float brightness, float contrast) {
        Bitmap result = bitmap.copy(bitmap.getConfig(), true);

        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xff;
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;

            // Apply contrast
            r = (int)((r - 128) * contrast + 128);
            g = (int)((g - 128) * contrast + 128);
            b = (int)((b - 128) * contrast + 128);

            // Apply brightness
            r += brightness;
            g += brightness;
            b += brightness;

            // Clamp values
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));

            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private void applyChanges() {
        Toast.makeText(this, "Saving crop...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Get normalized crop bounds (0..1)
                RectF cropBounds = cropImageView.getCropBounds();
                if (cropBounds == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: Could not read crop bounds", Toast.LENGTH_SHORT).show());
                    return;
                }

                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                PageSettingsDao sdao = db.pageSettingsDao();
                PageSettingsEntity current = sdao.getByPage(sheetId, pageNumber);

                // Snapshot previous settings into a hidden history layer (type 'CROP')
                try {
                    PageLayerDao ldao = db.pageLayerDao();
                    PageLayerEntity snap = new PageLayerEntity(sheetId, pageNumber, "__crop_snapshot__", "CROP");
                    if (current != null) {
                        snap.setCropLeft(current.getCropLeft());
                        snap.setCropTop(current.getCropTop());
                        snap.setCropRight(current.getCropRight());
                        snap.setCropBottom(current.getCropBottom());
                        snap.setRotation(current.getRotation());
                        snap.setBrightness(current.getBrightness());
                        snap.setContrast(current.getContrast());
                    } else {
                        // Defaults (full page) snapshot
                        snap.setCropLeft(0f);
                        snap.setCropTop(0f);
                        snap.setCropRight(1f);
                        snap.setCropBottom(1f);
                        snap.setRotation(0f);
                        snap.setBrightness(0f);
                        snap.setContrast(1f);
                    }
                    long snapId = ldao.insert(snap);
                    // Keep only latest snapshot active
                    ldao.deactivateOtherCropLayers(sheetId, pageNumber, snapId);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to snapshot previous crop settings", t);
                }

                // Write new settings (virtual layer)
                PageSettingsEntity settings = current != null ? current : new PageSettingsEntity(sheetId, pageNumber);
                settings.setCropLeft(cropBounds.left);
                settings.setCropTop(cropBounds.top);
                settings.setCropRight(cropBounds.right);
                settings.setCropBottom(cropBounds.bottom);
                settings.setRotation(currentRotation);
                settings.setBrightness(brightness);
                settings.setContrast(contrast);
                settings.setModifiedAt(System.currentTimeMillis());
                sdao.upsert(settings);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Crop saved", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error saving settings", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void restoreLastSnapshot() {
        Toast.makeText(this, "Restoring previous crop...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                PageLayerDao ldao = db.pageLayerDao();
                PageLayerEntity last = ldao.getLatestCropLayerForPage(sheetId, pageNumber);
                if (last == null) {
                    runOnUiThread(() -> Toast.makeText(this, "No previous crop to restore", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Move current settings to history as well
                try {
                    PageSettingsDao sdao = db.pageSettingsDao();
                    PageSettingsEntity current = sdao.getByPage(sheetId, pageNumber);
                    if (current != null) {
                        PageLayerEntity snap = new PageLayerEntity(sheetId, pageNumber, "__crop_snapshot__", "CROP");
                        snap.setCropLeft(current.getCropLeft());
                        snap.setCropTop(current.getCropTop());
                        snap.setCropRight(current.getCropRight());
                        snap.setCropBottom(current.getCropBottom());
                        snap.setRotation(current.getRotation());
                        snap.setBrightness(current.getBrightness());
                        snap.setContrast(current.getContrast());
                        long snapId = ldao.insert(snap);
                        ldao.deactivateOtherCropLayers(sheetId, pageNumber, snapId);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to snapshot current before restore", t);
                }

                // Apply snapshot back to virtual settings
                PageSettingsDao sdao = db.pageSettingsDao();
                PageSettingsEntity target = sdao.getByPage(sheetId, pageNumber);
                if (target == null) target = new PageSettingsEntity(sheetId, pageNumber);
                target.setCropLeft(last.getCropLeft());
                target.setCropTop(last.getCropTop());
                target.setCropRight(last.getCropRight());
                target.setCropBottom(last.getCropBottom());
                target.setRotation(last.getRotation());
                target.setBrightness(last.getBrightness());
                target.setContrast(last.getContrast());
                target.setModifiedAt(System.currentTimeMillis());
                sdao.upsert(target);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Restored previous crop", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error restoring snapshot", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (document != null) {
            try {
                document.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing document", e);
            }
        }
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
    private void runOnUiThreadSafe(Runnable action) {
        if (!isFinishing() && !(android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            runOnUiThread(action);
        }
    }

    private Bitmap renderWithAndroidPdfRenderer(File file, int pageIndex, float scale) {
        ParcelFileDescriptor fd = null;
        PdfRenderer pdfRenderer = null;
        PdfRenderer.Page page = null;
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fd);
            if (pageIndex < 0 || pageIndex >= pdfRenderer.getPageCount()) {
                pageIndex = 0;
            }
            page = pdfRenderer.openPage(pageIndex);
            int width = Math.max(1, (int) (page.getWidth() * scale));
            int height = Math.max(1, (int) (page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Android PdfRenderer failed in CropActivity", e);
            return null;
        } finally {
            try { if (page != null) page.close(); } catch (Throwable ignored) {}
            try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Throwable ignored) {}
            try { if (fd != null) fd.close(); } catch (Throwable ignored) {}
        }
    }

}
