package com.example.staffpad;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staffpad.views.CropImageView;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.PageLayerDao;
import com.example.staffpad.database.PageLayerEntity;
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
    private SeekBar brightnessSeekBar;
    private SeekBar contrastSeekBar;
    private SeekBar rotationSeekBar;
    private TextView brightnessLabel;
    private TextView contrastLabel;
    private TextView rotationLabel;

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
    }

    private void initializeViews() {
        cropImageView = findViewById(R.id.crop_image_view);
        rotateLeftButton = findViewById(R.id.rotate_left_button);
        rotateRightButton = findViewById(R.id.rotate_right_button);
        resetButton = findViewById(R.id.reset_button);
        applyButton = findViewById(R.id.apply_button);
        cancelButton = findViewById(R.id.cancel_button);
        brightnessSeekBar = findViewById(R.id.brightness_seekbar);
        contrastSeekBar = findViewById(R.id.contrast_seekbar);
        rotationSeekBar = findViewById(R.id.rotation_seekbar);
        brightnessLabel = findViewById(R.id.brightness_label);
        contrastLabel = findViewById(R.id.contrast_label);
        rotationLabel = findViewById(R.id.rotation_label);

        // Setup seekbars
        brightnessSeekBar.setMax(200); // -100 to 100
        brightnessSeekBar.setProgress(100); // 0 in the middle

        contrastSeekBar.setMax(150); // 0.5 to 2.0
        contrastSeekBar.setProgress(50); // 1.0 in the middle
    }

    private void setupListeners() {
        rotateLeftButton.setOnClickListener(v -> rotateImage(-90));
        rotateRightButton.setOnClickListener(v -> rotateImage(90));
        resetButton.setOnClickListener(v -> resetImage());
        applyButton.setOnClickListener(v -> applyChanges());
        cancelButton.setOnClickListener(v -> finish());

        // Fine rotation: map 0..200 to -10..+10 degrees
        rotationSeekBar.setMax(200);
        rotationSeekBar.setProgress(100); // center -> 0°
        rotationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float degrees = (progress - 100) / 100f * 10f; // -10..+10
                currentRotation = degrees;
                rotationLabel.setText(String.format("Align: %.1f°", degrees));
                cropImageView.setRotation(currentRotation);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightness = (progress - 100); // Convert to -100 to 100
                brightnessLabel.setText(String.format("Brightness: %d", (int)brightness));
                applyFilters();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        contrastSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                contrast = 0.5f + (progress / 100f) * 1.5f; // Convert to 0.5 to 2.0
                contrastLabel.setText(String.format("Contrast: %.2f", contrast));
                applyFilters();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
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
        // Sync UI seekbar and label if available
        if (rotationSeekBar != null) {
            int progress = (int) ((currentRotation / 10f) * 100f) + 100; // map back to 0..200
            rotationSeekBar.setProgress(progress);
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
        brightnessSeekBar.setProgress(100);
        contrastSeekBar.setProgress(50);
        if (rotationSeekBar != null) rotationSeekBar.setProgress(100);
        if (rotationLabel != null) rotationLabel.setText("Align: 0°");

        if (originalBitmap != null) {
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
        Toast.makeText(this, "Saving as new layer...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Get normalized crop bounds (0..1)
                RectF cropBounds = cropImageView.getCropBounds();
                if (cropBounds == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: Could not read crop bounds", Toast.LENGTH_SHORT).show());
                    return;
                }

                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                PageLayerDao dao = db.pageLayerDao();

                // Determine next order index
                int maxOrder = dao.getMaxOrderIndex(sheetId, pageNumber);
                int nextOrder = maxOrder + 1;

                // Create and populate layer entity
                PageLayerEntity layer = new PageLayerEntity(sheetId, pageNumber, "Layer " + nextOrder, "CROP");
                layer.setOrderIndex(nextOrder);
                layer.setActive(true);
                layer.setCropLeft(cropBounds.left);
                layer.setCropTop(cropBounds.top);
                layer.setCropRight(cropBounds.right);
                layer.setCropBottom(cropBounds.bottom);
                layer.setRotation(currentRotation);
                layer.setBrightness(brightness);
                layer.setContrast(contrast);
                layer.setModifiedAt(System.currentTimeMillis());

                dao.insert(layer);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Layer saved", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error saving layer", e);
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
