package com.example.staffpad;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.LiveData;

import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.viewmodel.SheetViewModel;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.OnViewTapListener;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.example.staffpad.utils.SharedPreferencesHelper;
import com.example.staffpad.database.PageLayerEntity;
import com.example.staffpad.database.AppDatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.app.ActivityManager;
import android.content.Context;

public class SheetDetailFragment extends Fragment {
    private static final String TAG = "SheetDetailFragment";
    private static final String ARG_SHEET_ID = "sheet_id";
    private static final String ARG_INITIAL_PAGE = "initial_page";
    private long sheetId = -1;
    private SheetViewModel sheetViewModel;
    private PhotoView photoView;
    private List<PageLayerEntity> activeLayers = new ArrayList<>();
    private LiveData<List<PageLayerEntity>> activeLayersLiveData;

    private SharedPreferencesHelper preferencesHelper;
    private long currentSheetId = -1;
    private int currentPage = 0;
    private boolean isCropMode = false;
    private RectF cropRect = null;
    private Paint cropPaint;
    private Paint cropBorderPaint;

    private PDDocument document;
    private PDFRenderer renderer;
    private File currentPdfFile;
    private final Object renderLock = new Object();
    private boolean androidRendererOnly = false;
    private int altPageCount = -1;

    public SheetDetailFragment() {
        // Required empty public constructor
    }

    public static SheetDetailFragment newInstance(long sheetId) {
        SheetDetailFragment fragment = new SheetDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SHEET_ID, sheetId);
        fragment.setArguments(args);
        return fragment;
    }

    public static SheetDetailFragment newInstance(long sheetId, int initialPage) {
        SheetDetailFragment fragment = new SheetDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SHEET_ID, sheetId);
        args.putInt(ARG_INITIAL_PAGE, initialPage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sheetId = getArguments().getLong(ARG_SHEET_ID, -1);
            if (getArguments().containsKey(ARG_INITIAL_PAGE)) {
                currentPage = getArguments().getInt(ARG_INITIAL_PAGE, currentPage);
            }
        }

        // Initialize ViewModel
        sheetViewModel = new ViewModelProvider(requireActivity()).get(SheetViewModel.class);
        preferencesHelper = new SharedPreferencesHelper(requireContext());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sheet_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        photoView = view.findViewById(R.id.photo_view);

        if (sheetId != -1) {
            // Observe the sheet ONCE to avoid repeated re-renders when DB fields (like lastOpened) change
            final androidx.lifecycle.LiveData<com.example.staffpad.database.SheetEntity> live = sheetViewModel.getSheetById(sheetId);
            final androidx.lifecycle.Observer<com.example.staffpad.database.SheetEntity> onceObserver = new androidx.lifecycle.Observer<com.example.staffpad.database.SheetEntity>() {
                @Override
                public void onChanged(com.example.staffpad.database.SheetEntity sheet) {
                    // Remove observer immediately to prevent loops
                    live.removeObserver(this);
                    displaySheet(sheet);
                }
            };
            live.observe(getViewLifecycleOwner(), onceObserver);
        }
    }
    private void displaySheet(SheetEntity sheet) {
        if (sheet == null) {
            Log.e(TAG, "Sheet not found for id: " + sheetId);
            return;
        }

        Log.d(TAG, "Displaying sheet: " + sheet.getTitle() + " from file: " + sheet.getFilePath());
        currentSheetId = sheet.getId();

        // Update toolbar title
        try {
            ((MainActivity) requireActivity()).updateToolbarTitle(sheet.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Could not update toolbar title", e);
        }

        // Load the PDF
        new Thread(() -> {
            try {
                File pdfFile = new File(sheet.getFilePath());
                currentPdfFile = pdfFile;

                if (!pdfFile.exists()) {
                    Log.e(TAG, "PDF file does not exist: " + pdfFile.getAbsolutePath());
                    runOnUiThread(() -> showErrorImage("Error: PDF file not found"));
                    return;
                }

                // Close previous document if exists
                if (document != null) {
                    try {
                        document.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing previous document", e);
                    }
                }

                boolean usePdfBox = shouldUsePdfBox(pdfFile);
                if (usePdfBox) {
                    PDDocument newDoc = null;
                    PDFRenderer newRenderer = null;
                    boolean swapped = false;
                    try {
                        // Load into locals first to avoid losing references on race conditions
                        newDoc = PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly());
                        newRenderer = new PDFRenderer(newDoc);
                        // Atomically swap under lock and close any previous document
                        synchronized (renderLock) {
                            PDDocument oldDoc = document;
                            document = newDoc;
                            renderer = newRenderer;
                            androidRendererOnly = false;
                            altPageCount = -1;
                            if (oldDoc != null && oldDoc != newDoc) {
                                try { oldDoc.close(); } catch (IOException ignore) {}
                            }
                            swapped = true;
                        }
                    } catch (OutOfMemoryError oom) {
                        Log.e(TAG, "PDDocument.load OOM, switching to Android PdfRenderer only", oom);
                        // Ensure any previously held PDDocument is closed before switching
                        synchronized (renderLock) {
                            if (document != null) {
                                try { document.close(); } catch (IOException ignore) {}
                            }
                            document = null;
                            renderer = null;
                            androidRendererOnly = true;
                        }
                        // Determine page count via Android PdfRenderer
                        try {
                            ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                            PdfRenderer pr = new PdfRenderer(fd);
                            altPageCount = pr.getPageCount();
                            pr.close();
                            fd.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to get page count via Android PdfRenderer", e);
                            altPageCount = -1;
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "PDFBox load/render init failed; will fallback to Android PdfRenderer", t);
                        // Ensure locals are closed if swap didn't happen
                        if (!swapped) {
                            try { if (newDoc != null) newDoc.close(); } catch (IOException ignore) {}
                        }
                        synchronized (renderLock) {
                            document = null;
                            renderer = null;
                            androidRendererOnly = true;
                        }
                        try {
                            ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                            PdfRenderer pr = new PdfRenderer(fd);
                            altPageCount = pr.getPageCount();
                            pr.close();
                            fd.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to get page count via Android PdfRenderer", e);
                            altPageCount = -1;
                        }
                    }
                } else {
                    // Skip PDFBox entirely due to low-memory/large file conditions
                    Log.w(TAG, "Skipping PDFBox load due to memory/file-size constraints; using Android PdfRenderer only");
                    // Ensure any previously held PDDocument is closed before switching
                    synchronized (renderLock) {
                        if (document != null) {
                            try { document.close(); } catch (IOException ignore) {}
                        }
                        document = null;
                        renderer = null;
                        androidRendererOnly = true;
                    }
                    try {
                        ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        PdfRenderer pr = new PdfRenderer(fd);
                        altPageCount = pr.getPageCount();
                        pr.close();
                        fd.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to get page count via Android PdfRenderer", e);
                        altPageCount = -1;
                    }
                }

                // Render current page
                int total = getTotalPageCountInternal(pdfFile);
                if (total > 0 && currentPage >= total) {
                    currentPage = 0;
                }

                // Render base page with PdfBox; fallback to Android PdfRenderer on failure
                Bitmap baseBitmap;
                try {
                    float scale = (!androidRendererOnly && renderer != null) ? getPdfBoxRenderScale() : getAndroidRenderScale();
                    synchronized (renderLock) {
                        baseBitmap = renderer != null ? renderer.renderImage(currentPage, scale) : null;
                    }
                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "PdfBox render OOM, switching to Android PdfRenderer", oom);
                    switchToAndroidRendererOnly(pdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = renderWithAndroidPdfRenderer(pdfFile, currentPage, scale);
                } catch (Throwable t) {
                    Log.e(TAG, "PdfBox render failed, falling back to Android PdfRenderer", t);
                    switchToAndroidRendererOnly(pdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = renderWithAndroidPdfRenderer(pdfFile, currentPage, scale);
                }

                if (baseBitmap == null) {
                    runOnUiThread(() -> showErrorImage("Error rendering PDF page"));
                    return;
                }

                // If fragment is no longer attached, don't proceed
                if (!isAdded()) {
                    return;
                }

                // Load and apply layers
                loadAndApplyLayers(sheet.getId(), currentPage, baseBitmap);

            } catch (Exception e) {
                Log.e(TAG, "Error loading PDF", e);
                runOnUiThread(() -> showErrorImage("Error loading PDF: " + e.getMessage()));
            }
        }).start();
    }

    private void loadAndApplyLayers(long sheetId, int pageNumber, Bitmap baseBitmap) {
        // Ensure LiveData.observe is invoked on the main thread
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread(() -> loadAndApplyLayers(sheetId, pageNumber, baseBitmap));
            return;
        }

        // Ensure fragment is attached and view lifecycle owner is available
        if (!isAdded()) {
            return;
        }
        androidx.lifecycle.LifecycleOwner vlo = getViewLifecycleOwnerLiveData().getValue();
        if (vlo == null) {
            // View lifecycle owner not ready; skip to avoid crash
            return;
        }

        Context appCtx = getActivity() != null ? getActivity().getApplicationContext() : null;
        if (appCtx == null) {
            return;
        }
        AppDatabase db = AppDatabase.getDatabase(appCtx);

        // Remove previous observer to avoid accumulation
        if (activeLayersLiveData != null) {
            try {
                activeLayersLiveData.removeObservers(vlo);
            } catch (Exception ignored) {}
        }

        // Get active layers for this page and observe once per page
        activeLayersLiveData = db.pageLayerDao().getActiveLayersForPage(sheetId, pageNumber);
        activeLayersLiveData.observe(vlo, layers -> {
            if (layers == null || layers.isEmpty()) {
                // No layers, just show base bitmap
                displayBitmap(baseBitmap);
                return;
            }

            // Apply layers on background thread
            new Thread(() -> {
                try {
                    Bitmap compositeBitmap = applyLayers(baseBitmap, layers);
                    runOnUiThread(() -> displayBitmap(compositeBitmap));
                } catch (Exception e) {
                    Log.e(TAG, "Error applying layers", e);
                    runOnUiThread(() -> displayBitmap(baseBitmap));
                }
            }).start();
        });
    }

    private void displayBitmap(Bitmap bitmap) {
        if (photoView != null && bitmap != null) {
            if (photoView.getVisibility() != View.VISIBLE) {
                photoView.setVisibility(View.VISIBLE);
            }
            photoView.setImageBitmap(bitmap);
            photoView.setMaximumScale(5.0f);

            // Update page indicator
            int total = getTotalPageCount();
            if (total > 0) {
                updatePageIndicator(currentPage + 1, total);
            }

            // Setup page navigation
            setupPageNavigation();

            // Persist the current view (sheet + page) immediately after first successful render
            if (currentSheetId != -1) {
                try {
                    preferencesHelper.saveLastViewedPage(currentSheetId, currentPage);
                    Log.d(TAG, "displayBitmap: Saved last viewed page " + currentPage + " for sheet " + currentSheetId);
                } catch (Exception e) {
                    Log.w(TAG, "displayBitmap: Failed to save last viewed page", e);
                }
            }

            Log.d(TAG, "Bitmap displayed with layers applied");
        }
    }

    private Bitmap applyLayers(Bitmap baseBitmap, List<PageLayerEntity> layers) {
        // Start with a mutable working bitmap to avoid multiple full-size allocations
        Bitmap working = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

        for (PageLayerEntity layer : layers) {
            if (!layer.isActive()) continue;

            try {
                // Apply crop (creates a new bitmap). Recycle previous to save memory.
                if (layer.hasCrop()) {
                    Bitmap newBmp = applyCrop(working, layer);
                    if (working != baseBitmap && working != newBmp && !working.isRecycled()) {
                        working.recycle();
                    }
                    working = newBmp;
                }

                // Apply rotation (creates a new bitmap). Recycle previous to save memory.
                if (layer.hasRotation()) {
                    Bitmap newBmp = applyRotation(working, layer.getRotation());
                    if (working != baseBitmap && working != newBmp && !working.isRecycled()) {
                        working.recycle();
                    }
                    working = newBmp;
                }

                // Apply adjustments in-place to avoid extra allocation
                if (layer.hasAdjustments()) {
                    applyAdjustmentsInPlace(working, layer.getBrightness(), layer.getContrast());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error applying layer: " + layer.getLayerName(), e);
            }
        }

        return working;
    }

    private Bitmap applyCrop(Bitmap bitmap, PageLayerEntity layer) {
        int left = (int)(layer.getCropLeft() * bitmap.getWidth());
        int top = (int)(layer.getCropTop() * bitmap.getHeight());
        int right = (int)(layer.getCropRight() * bitmap.getWidth());
        int bottom = (int)(layer.getCropBottom() * bitmap.getHeight());

        int width = right - left;
        int height = bottom - top;

        // Clamp values
        left = Math.max(0, Math.min(left, bitmap.getWidth() - 1));
        top = Math.max(0, Math.min(top, bitmap.getHeight() - 1));
        width = Math.max(1, Math.min(width, bitmap.getWidth() - left));
        height = Math.max(1, Math.min(height, bitmap.getHeight() - top));

        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }

    public void onPageChanged(int newPageNumber) {
        this.currentPage = newPageNumber;

        // Save immediately on page change
        if (currentSheetId != -1) {
            preferencesHelper.saveLastViewedPage(currentSheetId, currentPage);
            Log.d(TAG, "Saved current page: " + currentPage + " for sheet: " + currentSheetId);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save the current page when leaving the fragment
        if (currentSheetId != -1) {
            preferencesHelper.saveLastViewedPage(currentSheetId, currentPage);
            Log.d(TAG, "onPause: Saved last viewed page: " + currentPage);
        }
    }

    private Bitmap applyRotation(Bitmap bitmap, float rotation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap applyAdjustments(Bitmap bitmap, float brightness, float contrast) {
        // Backward-compatible wrapper: perform in-place on a copy to preserve old behavior
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        applyAdjustmentsInPlace(copy, brightness, contrast);
        return copy;
    }

    private void applyAdjustmentsInPlace(Bitmap target, float brightness, float contrast) {
        if (target == null) return;
        int width = target.getWidth();
        int height = target.getHeight();
        int[] pixels = new int[width * height];
        target.getPixels(pixels, 0, width, 0, 0, width, height);

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

        target.setPixels(pixels, 0, width, 0, 0, width, height);
    }
    public void setCurrentPage(int pageNumber) {
        this.currentPage = pageNumber;

        int total = getTotalPageCount();
        if (total <= 0) {
            return;
        }
        if (pageNumber < 0 || pageNumber >= total) {
            return;
        }

        new Thread(() -> {
            try {
                Bitmap baseBitmap;
                try {
                    float scale = (!androidRendererOnly && renderer != null) ? getPdfBoxRenderScale() : getAndroidRenderScale();
                    if (!androidRendererOnly && renderer != null) {
                        synchronized (renderLock) {
                            baseBitmap = renderer.renderImage(pageNumber, scale);
                        }
                    } else {
                        baseBitmap = currentPdfFile != null ? renderWithAndroidPdfRenderer(currentPdfFile, pageNumber, scale) : null;
                    }
                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "PdfBox render OOM on page change, switching to Android PdfRenderer", oom);
                    if (currentPdfFile != null) switchToAndroidRendererOnly(currentPdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = currentPdfFile != null ? renderWithAndroidPdfRenderer(currentPdfFile, pageNumber, scale) : null;
                } catch (Throwable t) {
                    Log.e(TAG, "PdfBox render failed on page change, using Android PdfRenderer", t);
                    if (currentPdfFile != null) switchToAndroidRendererOnly(currentPdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = currentPdfFile != null ? renderWithAndroidPdfRenderer(currentPdfFile, pageNumber, scale) : null;
                }

                if (baseBitmap == null) {
                    runOnUiThread(() -> showErrorImage("Error rendering page"));
                    return;
                }

                // Apply layers and display on UI
                loadAndApplyLayers(sheetId, pageNumber, baseBitmap);

                // Update page indicator and save page
                runOnUiThread(() -> {
                    int t = getTotalPageCount();
                    if (t > 0) {
                        updatePageIndicator(pageNumber + 1, t);
                    }
                    onPageChanged(pageNumber);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error rendering page change", e);
                runOnUiThread(() -> showErrorImage("Error rendering page"));
            }
        }).start();
    }


    private void setupPageNavigation() {
        if (photoView == null) return;

        // Calculate tap zone width in pixels for ~1.5 inches
        final float zonePx = 1.5f * getResources().getDisplayMetrics().xdpi;

        photoView.setOnViewTapListener(new OnViewTapListener() {
            @Override
            public void onViewTap(View view, float x, float y) {
                int total = getTotalPageCount();
                if (total <= 0) return;

                int width = view.getWidth();
                if (x <= zonePx) {
                    // Left zone: previous page
                    if (currentPage > 0) {
                        setCurrentPage(currentPage - 1);
                    }
                } else if (x >= width - zonePx) {
                    // Right zone: next page
                    if (currentPage < total - 1) {
                        setCurrentPage(currentPage + 1);
                    }
                } else {
                    // Middle tap: do nothing (could toggle UI later)
                    Log.d(TAG, "Middle area tapped; no navigation");
                }
            }
        });
    }

    private void updatePageIndicator(int current, int total) {
        try {
            MainActivity activity = (MainActivity) requireActivity();
            TextView pageIndicator = activity.findViewById(R.id.page_indicator);
            if (pageIndicator != null) {
                pageIndicator.setText(current + "/" + total);
            }
        } catch (Exception e) {
            Log.e("SheetDetail", "Could not update page indicator", e);
        }
    }

    private void showErrorImage(String message) {
        if (photoView == null) return;

        Bitmap errorBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(errorBitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(36);
        paint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(message, 400, 300, paint);
        photoView.setImageBitmap(errorBitmap);
    }

    // Add cleanup in onDestroy and onDestroyView to ensure PDDocument is closed
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        synchronized (renderLock) {
            if (document != null) {
                try { document.close(); } catch (IOException e) { Log.e(TAG, "Error closing document in onDestroyView", e); }
            }
            document = null;
            renderer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized (renderLock) {
            if (document != null) {
                try { document.close(); } catch (IOException e) { Log.e(TAG, "Error closing document", e); }
            }
            document = null;
            renderer = null;
        }
    }

    private boolean attemptedAutoRefresh = false;

    @Override
    public void onResume() {
        super.onResume();
        // Fail-safe: if no image is displayed yet, try a one-time refresh after a short delay
        if (photoView != null && photoView.getDrawable() == null) {
            photoView.postDelayed(() -> {
                if (isAdded() && photoView != null && photoView.getDrawable() == null) {
                    if (currentPdfFile != null) {
                        if (!attemptedAutoRefresh) {
                            attemptedAutoRefresh = true;
                            Log.d(TAG, "Auto-refreshing page display onResume");
                            refreshPage();
                        }
                    } else {
                        // If PDF file isn't ready yet, try once to kick rendering via setCurrentPage
                        if (!attemptedAutoRefresh) {
                            attemptedAutoRefresh = true;
                            Log.d(TAG, "Auto-refreshing via setCurrentPage onResume (no file yet)");
                            setCurrentPage(currentPage);
                        }
                    }
                }
            }, 200);
        }
    }

    // Helper method to run on UI thread
    private void runOnUiThread(Runnable action) {
        if (getActivity() != null && isAdded()) {
            getActivity().runOnUiThread(action);
        }
    }

    private boolean shouldUsePdfBox(File pdfFile) {
        // Skip PDFBox if device is under memory pressure or the file is large
        if (pdfFile != null) {
            long fileSize = pdfFile.length();
            // If PDF is larger than 25MB, avoid PDFBox to prevent OOM on constrained devices
            if (fileSize > 25L * 1024L * 1024L) {
                return false;
            }
        }
        return !isLowMemory();
    }

    private boolean isLowMemory() {
        try {
            ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mi);
            boolean sysLow = mi != null && (mi.lowMemory || mi.availMem < 100L * 1024L * 1024L); // <100MB avail

            Runtime rt = Runtime.getRuntime();
            long freeHeap = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            boolean heapLow = freeHeap < 32L * 1024L * 1024L; // <32MB free on heap

            return sysLow || heapLow;
        } catch (Throwable t) {
            // If anything goes wrong, err on the safe side and report low memory false
            return false;
        }
    }

    private float getAndroidRenderScale() {
        // Use a smaller scale when low on memory to reduce bitmap size
        return isLowMemory() ? 1.2f : 1.5f;
    }

    private float getPdfBoxRenderScale() {
        // Slightly reduced scale for PDFBox to lower memory pressure
        return isLowMemory() ? 1.3f : 1.6f;
    }

    private void switchToAndroidRendererOnly(File pdfFile) {
        // Close and null PDFBox resources, set androidRendererOnly, and cache page count
        try {
            if (document != null) {
                document.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing PDDocument during switchToAndroidRendererOnly", e);
        } finally {
            document = null;
            renderer = null;
        }
        androidRendererOnly = true;
        // Cache page count via Android PdfRenderer if not already known
        if (altPageCount <= 0 && pdfFile != null && pdfFile.exists()) {
            ParcelFileDescriptor fd = null;
            PdfRenderer pr = null;
            try {
                fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pr = new PdfRenderer(fd);
                altPageCount = pr.getPageCount();
            } catch (Exception e) {
                Log.e(TAG, "Failed to cache page count in switchToAndroidRendererOnly", e);
            } finally {
                try { if (pr != null) pr.close(); } catch (Throwable ignored) {}
                try { if (fd != null) fd.close(); } catch (Throwable ignored) {}
            }
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
            Log.e(TAG, "Android PdfRenderer failed", e);
            return null;
        } finally {
            try { if (page != null) page.close(); } catch (Throwable ignored) {}
            try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Throwable ignored) {}
            try { if (fd != null) fd.close(); } catch (Throwable ignored) {}
        }
    }

    private int getTotalPageCount() {
        try {
            if (document != null) return document.getNumberOfPages();
            if (altPageCount > 0) return altPageCount;
        } catch (Throwable ignored) {}
        return 0;
    }

    private int getTotalPageCountInternal(File pdfFile) {
        int total = getTotalPageCount();
        if (total > 0) return total;
        // As a last resort, try to determine via Android PdfRenderer and cache
        ParcelFileDescriptor fd = null;
        PdfRenderer pr = null;
        try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pr = new PdfRenderer(fd);
            altPageCount = pr.getPageCount();
        } catch (Exception e) {
            Log.e(TAG, "Could not determine page count via Android PdfRenderer", e);
        } finally {
            try { if (pr != null) pr.close(); } catch (Throwable ignored) {}
            try { if (fd != null) fd.close(); } catch (Throwable ignored) {}
        }
        return getTotalPageCount();
    }

    public void refreshPage() {
        // Re-render the current page without adding new LiveData observers to avoid leaks/OOM
        if (currentPdfFile == null) {
            Log.w(TAG, "refreshPage: currentPdfFile is null; skipping re-render");
            return;
        }
        final int page = currentPage;
        new Thread(() -> {
            try {
                Bitmap baseBitmap;
                try {
                    float scale = (!androidRendererOnly && renderer != null) ? getPdfBoxRenderScale() : getAndroidRenderScale();
                    if (!androidRendererOnly && renderer != null) {
                        synchronized (renderLock) {
                            baseBitmap = renderer.renderImage(page, scale);
                        }
                    } else {
                        baseBitmap = renderWithAndroidPdfRenderer(currentPdfFile, page, scale);
                    }
                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "PdfBox render OOM in refreshPage, switching to Android PdfRenderer", oom);
                    if (currentPdfFile != null) switchToAndroidRendererOnly(currentPdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = renderWithAndroidPdfRenderer(currentPdfFile, page, scale);
                } catch (Throwable t) {
                    Log.e(TAG, "PdfBox render failed in refreshPage, using Android PdfRenderer", t);
                    if (currentPdfFile != null) switchToAndroidRendererOnly(currentPdfFile);
                    float scale = getAndroidRenderScale();
                    baseBitmap = renderWithAndroidPdfRenderer(currentPdfFile, page, scale);
                }

                if (baseBitmap == null) {
                    runOnUiThread(() -> showErrorImage("Error rendering page"));
                    return;
                }

                loadAndApplyLayers(sheetId, page, baseBitmap);

                runOnUiThread(() -> {
                    int t = getTotalPageCount();
                    if (t > 0) updatePageIndicator(page + 1, t);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing page", e);
                runOnUiThread(() -> showErrorImage("Error rendering page"));
            }
        }).start();
    }//

    public int getCurrentPage() {
        return this.currentPage;
    }

    public long getSheetId() {
        return sheetId;
    }
}