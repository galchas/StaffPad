package com.example.staffpad;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staffpad.views.CropImageView;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;

public class CropActivity extends AppCompatActivity {

    private static final String TAG = "CropActivity";

    public static final String EXTRA_SHEET_ID = "sheet_id";
    public static final String EXTRA_PAGE_NUMBER = "page_number";

    private CropImageView cropImageView;
    private SeekBar rotationSeekBar;
    private TextView rotationValueText;
    private TextView pageIndicatorText;
    private ImageButton prevPageButton;
    private ImageButton nextPageButton;
    private ImageButton rotateLeftFineButton;
    private ImageButton rotateRightFineButton;

    private long sheetId;
    private int currentPage = 0;
    private int totalPages = 0;
    private String pdfFilePath;

    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;

    // Continuous rotation
    private Handler rotationHandler = new Handler();
    private Runnable rotationRunnable;
    private boolean isRotating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        Log.d(TAG, "CropActivity started");

        // Get extras
        sheetId = getIntent().getLongExtra(EXTRA_SHEET_ID, -1);
        currentPage = getIntent().getIntExtra(EXTRA_PAGE_NUMBER, 0);

        if (sheetId == -1) {
            Log.e(TAG, "Invalid sheet ID");
            Toast.makeText(this, "Error: Invalid sheet ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loading sheet ID: " + sheetId + ", page: " + currentPage);

        // Initialize views
        initViews();

        // Setup listeners
        setupListeners();

        // Load PDF
        loadPdfForSheet();
    }

    private void initViews() {
        cropImageView = findViewById(R.id.crop_image_view);
        rotationSeekBar = findViewById(R.id.rotation_seekbar);
        rotationValueText = findViewById(R.id.rotation_value);
        pageIndicatorText = findViewById(R.id.page_indicator);
        prevPageButton = findViewById(R.id.prev_page_button);
        nextPageButton = findViewById(R.id.next_page_button);
        rotateLeftFineButton = findViewById(R.id.rotate_left_fine);
        rotateRightFineButton = findViewById(R.id.rotate_right_fine);
    }

    private void setupListeners() {
        // Cancel button
        findViewById(R.id.cancel_button).setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked");
            finish();
        });

        // Done button
        findViewById(R.id.done_button).setOnClickListener(v -> {
            Log.d(TAG, "Done button clicked");
            saveCropSettings();
        });

        // Reset button
        findViewById(R.id.reset_button).setOnClickListener(v -> {
            Log.d(TAG, "Reset button clicked");
            cropImageView.reset();
            rotationSeekBar.setProgress(4500); // Reset to 0 degrees (middle of range)
        });

        // Rotation seekbar - much more sensitive with 0.01 degree precision
        rotationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Convert 0-9000 to -45 to +45 degrees with 0.01 degree precision
                // 9000 steps for 90 degree range = 0.01 degree per step
                float angle = (progress - 4500) / 100.0f;
                cropImageView.setRotation(angle);
                rotationValueText.setText(String.format("%.2f°", angle));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set seekbar to middle (0 degrees)
        rotationSeekBar.setProgress(4500);

        // Fine rotation buttons - 0.01 degree increments
        rotateLeftFineButton.setOnClickListener(v -> {
            int currentProgress = rotationSeekBar.getProgress();
            rotationSeekBar.setProgress(currentProgress - 1); // -0.01 degree
        });

        rotateRightFineButton.setOnClickListener(v -> {
            int currentProgress = rotationSeekBar.getProgress();
            rotationSeekBar.setProgress(currentProgress + 1); // +0.01 degree
        });

        // Long press for continuous rotation
        rotateLeftFineButton.setOnLongClickListener(v -> {
            startContinuousRotation(-1);
            return true;
        });

        rotateRightFineButton.setOnLongClickListener(v -> {
            startContinuousRotation(1);
            return true;
        });

        // Stop continuous rotation on touch release
        rotateLeftFineButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopContinuousRotation();
            }
            return false;
        });

        rotateRightFineButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopContinuousRotation();
            }
            return false;
        });

        // Page navigation
        prevPageButton.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                loadPage(currentPage);
            }
        });

        nextPageButton.setOnClickListener(v -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                loadPage(currentPage);
            }
        });

        // Auto crop button
        findViewById(R.id.auto_crop_button).setOnClickListener(v -> {
            Toast.makeText(this, "Auto crop coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Apply to all button
        findViewById(R.id.apply_to_all_button).setOnClickListener(v -> {
            Toast.makeText(this, "Apply to all pages coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Crop change listener
        cropImageView.setCropChangeListener((cropRect, rotation) -> {
            // Optional: Save intermediate changes or show preview
        });
    }

    private void loadPdfForSheet() {
        Log.d(TAG, "Loading PDF for sheet ID: " + sheetId);

        // Get PDF file path from database
        new Thread(() -> {
            try {
                // Get sheet from database
                com.example.staffpad.database.AppDatabase database =
                        com.example.staffpad.database.AppDatabase.getDatabase(getApplicationContext());

                // Use repository to get sheet
                com.example.staffpad.database.repository.SheetRepository repository =
                        new com.example.staffpad.database.repository.SheetRepository(getApplication());

                // Observe on main thread
                runOnUiThread(() -> {
                    repository.getSheetById(sheetId).observe(this, sheet -> {
                        if (sheet == null) {
                            Log.e(TAG, "Sheet not found in database");
                            Toast.makeText(this, "Error: Sheet not found", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        pdfFilePath = sheet.getFilePath();
                        Log.d(TAG, "PDF file path: " + pdfFilePath);
                        loadPdfFile();
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading sheet", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading sheet: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    private void loadPdfFile() {
        Log.d(TAG, "Loading PDF file: " + pdfFilePath);

        new Thread(() -> {
            try {
                // Load PDF
                File pdfFile = new File(pdfFilePath);
                if (!pdfFile.exists()) {
                    Log.e(TAG, "PDF file not found: " + pdfFilePath);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error: PDF file not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                Log.d(TAG, "PDF file exists, loading document");
                pdfDocument = PDDocument.load(pdfFile);
                pdfRenderer = new PDFRenderer(pdfDocument);
                totalPages = pdfDocument.getNumberOfPages();

                Log.d(TAG, "PDF loaded successfully, " + totalPages + " pages");

                // Load initial page
                runOnUiThread(() -> {
                    updatePageIndicator();
                    loadPage(currentPage);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading PDF file", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading PDF: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    private void loadPage(int pageNumber) {
        if (pdfRenderer == null) {
            Log.e(TAG, "PDF renderer is null");
            return;
        }

        Log.d(TAG, "Loading page " + pageNumber);

        new Thread(() -> {
            try {
                // Render page to bitmap
                Bitmap bitmap = pdfRenderer.renderImage(pageNumber);
                Log.d(TAG, "Page rendered: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                runOnUiThread(() -> {
                    cropImageView.setImageBitmap(bitmap);
                    updatePageIndicator();
                    updateNavigationButtons();

                    // Load saved crop settings for this page if available
                    loadCropSettingsForPage(pageNumber);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading page " + pageNumber, e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading page: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updatePageIndicator() {
        pageIndicatorText.setText(String.format("Page %d of %d",
                currentPage + 1, totalPages));
    }

    private void updateNavigationButtons() {
        prevPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled(currentPage < totalPages - 1);

        // Update button alpha to show enabled/disabled state
        prevPageButton.setAlpha(currentPage > 0 ? 1.0f : 0.3f);
        nextPageButton.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.3f);
    }

    private void loadCropSettingsForPage(int pageNumber) {
        // TODO: Load saved crop settings from database
        // For now, we'll use default (full page)
        Log.d(TAG, "Loading crop settings for page " + pageNumber + " (not yet implemented)");
    }

    private void saveCropSettings() {
        RectF cropRect = cropImageView.getCropRect();
        float rotation = cropImageView.getRotation();

        Log.d(TAG, "Saving crop settings - Rotation: " + rotation + "°");
        Log.d(TAG, "Crop rect: " + cropRect.toString());

        // TODO: Save to database
        Toast.makeText(this,
                String.format("Crop saved: Rotation=%.2f°", rotation),
                Toast.LENGTH_SHORT).show();

        // Return result
        setResult(RESULT_OK);
        finish();
    }

    private void startContinuousRotation(int direction) {
        if (isRotating) return;

        Log.d(TAG, "Starting continuous rotation, direction: " + direction);
        isRotating = true;
        rotationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRotating) {
                    int currentProgress = rotationSeekBar.getProgress();
                    int newProgress = currentProgress + direction;

                    // Clamp to valid range
                    if (newProgress >= 0 && newProgress <= 9000) {
                        rotationSeekBar.setProgress(newProgress);
                        rotationHandler.postDelayed(this, 50); // Update every 50ms
                    } else {
                        stopContinuousRotation();
                    }
                }
            }
        };
        rotationHandler.post(rotationRunnable);
    }

    private void stopContinuousRotation() {
        Log.d(TAG, "Stopping continuous rotation");
        isRotating = false;
        if (rotationRunnable != null) {
            rotationHandler.removeCallbacks(rotationRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "CropActivity destroyed");

        // Stop continuous rotation
        stopContinuousRotation();

        // Clean up PDF resources
        if (pdfDocument != null) {
            try {
                pdfDocument.close();
                Log.d(TAG, "PDF document closed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing PDF document", e);
            }
        }
    }
}