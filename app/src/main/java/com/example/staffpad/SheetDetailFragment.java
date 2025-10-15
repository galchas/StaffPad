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
import com.example.staffpad.database.PageSettingsEntity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.app.ActivityManager;
import android.content.Context;

public class SheetDetailFragment extends Fragment {
    // When true, we suppress compositing the persisted annotation bitmap while in annotation mode.
    private boolean suppressAnnotationComposite = false;
    private static final String TAG = "SheetDetailFragment";
    private static final String ARG_SHEET_ID = "sheet_id";
    private static final String ARG_INITIAL_PAGE = "initial_page";
    private long sheetId = -1;
    private SheetViewModel sheetViewModel;
    private PhotoView photoView;
    private List<PageLayerEntity> activeLayers = new ArrayList<>();
    private LiveData<List<PageLayerEntity>> activeLayersLiveData;
    private LiveData<com.example.staffpad.database.PageSettingsEntity> pageSettingsLiveData;

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

    // Annotation UI references for external control
    private View annotationToolbar;
    private com.example.staffpad.views.AnnotationOverlayView annotationOverlay;

    // Pen preset UI
    private RecyclerView penPresetList;
    private final List<PenPreset> penPresets = new ArrayList<>();
    private PenPresetAdapter penPresetAdapter;

    private static class PenPreset {
        int color;
        float widthPx;
        int alpha;
        String name;
        PenPreset(String name, int color, float widthPx, int alpha) {
            this.name = name; this.color = color; this.widthPx = widthPx; this.alpha = alpha;
        }
    }

    private class PenPresetAdapter extends RecyclerView.Adapter<PenPresetAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            android.widget.FrameLayout root = new android.widget.FrameLayout(ctx);
            int pad = (int)(6 * ctx.getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            int sz = (int)(32 * ctx.getResources().getDisplayMetrics().density);
            android.view.View circle = new android.view.View(ctx);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(sz, sz);
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            root.addView(circle, lp);
            return new VH(root, circle);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            PenPreset p = penPresets.get(position);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(p.color);
            int stroke = (int)(2 * holder.itemView.getResources().getDisplayMetrics().density);
            circle.setStroke(stroke, 0xFFFFFFFF);
            holder.colorView.setBackground(circle);
            holder.itemView.setOnClickListener(v -> {
                if (annotationOverlay != null) {
                    annotationOverlay.setPen(p.color, p.widthPx, p.alpha);
                    annotationOverlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.PEN);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                showEditPresetDialog(position);
                return true;
            });
        }
        @Override public int getItemCount() { return penPresets.size(); }
        class VH extends RecyclerView.ViewHolder {
            final View colorView;
            VH(@NonNull View itemView, View colorView) { super(itemView); this.colorView = colorView; }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        photoView = view.findViewById(R.id.photo_view);

        // Annotation UI setup (no standalone entry button; controlled via toolbox)
        annotationToolbar = view.findViewById(R.id.annotation_toolbar);
        annotationOverlay = view.findViewById(R.id.annotation_overlay);
        View toolbar = annotationToolbar;
        com.example.staffpad.views.AnnotationOverlayView overlay = annotationOverlay;
        if (toolbar != null && overlay != null) {
            // Draggable toolbar (vertical drag only as per requirement)
            toolbar.setOnTouchListener(new View.OnTouchListener() {
                float dY;
                @Override public boolean onTouch(View v2, android.view.MotionEvent e) {
                    View parent = (View) v2.getParent();
                    switch (e.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            dY = v2.getY() - e.getRawY();
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE:
                            float newY = e.getRawY() + dY;
                            newY = Math.max(0, Math.min(newY, parent.getHeight() - v2.getHeight()));
                            v2.setY(newY);
                            return true;
                        default:
                            return false;
                    }
                }
            });
            View btnText = view.findViewById(R.id.btn_tool_text);
            View btnPen = view.findViewById(R.id.btn_tool_pen);
            View btnEraser = view.findViewById(R.id.btn_tool_eraser);
            View btnUndo = view.findViewById(R.id.btn_undo);
            View btnRedo = view.findViewById(R.id.btn_redo);
            View btnSave = view.findViewById(R.id.btn_save_annotations);
            View btnClear = view.findViewById(R.id.btn_clear_annotations);
            View btnClose = view.findViewById(R.id.btn_exit_annotations);

            // Pen preset list setup
            penPresetList = view.findViewById(R.id.pen_preset_list);
            if (penPresetList != null) {
                penPresetList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
                penPresetAdapter = new PenPresetAdapter();
                penPresetList.setAdapter(penPresetAdapter);
                loadPenPresetsFromPrefs();
                penPresetAdapter.notifyDataSetChanged();
            }

            btnText.setOnClickListener(v -> overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.TEXT));
            btnPen.setOnClickListener(v -> {
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
                b.setTitle("Pen Settings");

                final android.widget.LinearLayout container = new android.widget.LinearLayout(requireContext());
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                int pad = (int)(12 * getResources().getDisplayMetrics().density);
                container.setPadding(pad, pad, pad, pad);

                // Preview view
                final android.graphics.Paint previewPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                previewPaint.setStyle(android.graphics.Paint.Style.STROKE);
                previewPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                previewPaint.setColor(android.graphics.Color.BLACK);
                previewPaint.setStrokeWidth(6f);

                final android.view.View preview = new android.view.View(requireContext()) {
                    @Override protected void onDraw(android.graphics.Canvas c) {
                        super.onDraw(c);
                        int w = getWidth();
                        int h = getHeight();
                        c.drawColor(0xFFEFEFEF);
                        int cy = h/2;
                        c.drawLine(pad, cy, w - pad, cy, previewPaint);
                    }
                };
                int pvH = (int)(64 * getResources().getDisplayMetrics().density);
                container.addView(preview, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, pvH));

                // Width slider (increased max)
                final android.widget.SeekBar widthSeek = new android.widget.SeekBar(requireContext());
                widthSeek.setMax(80);
                widthSeek.setProgress(6);
                widthSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        float w = Math.max(1, progress);
                        previewPaint.setStrokeWidth(w);
                        preview.invalidate();
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
                });
                container.addView(widthSeek);

                // Opacity slider for pen alpha (0-100%)
                final android.widget.TextView alphaLabel = new android.widget.TextView(requireContext());
                alphaLabel.setText("Opacity");
                container.addView(alphaLabel);
                final android.widget.SeekBar alphaSeek = new android.widget.SeekBar(requireContext());
                alphaSeek.setMax(100);
                alphaSeek.setProgress(100);
                alphaSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        int a = (int) Math.round(progress * 2.55);
                        previewPaint.setAlpha(a);
                        preview.invalidate();
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
                });
                container.addView(alphaSeek);

                // Color picker view for pen color
                final com.skydoves.colorpickerview.ColorPickerView colorPicker = new com.skydoves.colorpickerview.ColorPickerView.Builder(requireContext()).build();
                // Use the library's default HSV palette per official docs (no explicit call needed)
                colorPicker.setInitialColor(previewPaint.getColor());
                colorPicker.setColorListener((com.skydoves.colorpickerview.listeners.ColorEnvelopeListener) (envelope, fromUser) -> {
                    previewPaint.setColor(envelope.getColor());
                    preview.invalidate();
                });
                int cpH = (int)(220 * getResources().getDisplayMetrics().density);
                container.addView(colorPicker, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, cpH));

                b.setView(container);
                b.setNegativeButton("Cancel", null);
                b.setPositiveButton("Use", (d, wbtn) -> {
                    int color = previewPaint.getColor();
                    float width = Math.max(1, widthSeek.getProgress());
                    int alpha = (int) Math.round(alphaSeek.getProgress() * 2.55);
                    overlay.setPen(color, width, alpha);
                    overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.PEN);
                });
                b.setNeutralButton("Save as preset", (d, wbtn) -> {
                    int color = previewPaint.getColor();
                    float width = Math.max(1, widthSeek.getProgress());
                    int alpha = (int) Math.round(alphaSeek.getProgress() * 2.55);
                    PenPreset newPreset = new PenPreset("Custom", color, width, alpha);
                    penPresets.add(0, newPreset);
                    savePenPresetsToPrefs();
                    if (penPresetAdapter != null) penPresetAdapter.notifyItemInserted(0);
                    if (penPresetList != null) penPresetList.scrollToPosition(0);
                    // Immediately use this pen configuration after saving
                    if (annotationOverlay != null) {
                        annotationOverlay.setPen(color, width, alpha);
                        annotationOverlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.PEN);
                    }
                });
                b.show();
            });
            btnEraser.setOnClickListener(v -> overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.ERASER));
            btnEraser.setOnLongClickListener(v -> {
                // Show eraser width preview dialog
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
                b.setTitle("Eraser Settings");
                final android.widget.LinearLayout container = new android.widget.LinearLayout(requireContext());
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                int pad = (int)(12 * getResources().getDisplayMetrics().density);
                container.setPadding(pad, pad, pad, pad);

                final android.graphics.Paint previewPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                previewPaint.setStyle(android.graphics.Paint.Style.STROKE);
                previewPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                previewPaint.setColor(0xFFCCCCCC);
                previewPaint.setStrokeWidth(Math.max(1f, overlay.getEraserWidth()));

                final android.view.View preview = new android.view.View(requireContext()) {
                    @Override protected void onDraw(android.graphics.Canvas c) {
                        super.onDraw(c);
                        int w = getWidth();
                        int h = getHeight();
                        c.drawColor(0xFFEFEFEF);
                        float cx = w/2f;
                        float cy = h/2f;
                        // draw a circle representing eraser size
                        c.drawCircle(cx, cy, previewPaint.getStrokeWidth()/2f, previewPaint);
                    }
                };
                int pvH = (int)(120 * getResources().getDisplayMetrics().density);
                container.addView(preview, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, pvH));

                final android.widget.SeekBar widthSeek = new android.widget.SeekBar(requireContext());
                widthSeek.setMax(200);
                widthSeek.setProgress((int)Math.max(1f, overlay.getEraserWidth()));
                widthSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        float wpx = Math.max(1, progress);
                        previewPaint.setStrokeWidth(wpx);
                        preview.invalidate();
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
                });
                container.addView(widthSeek);

                b.setView(container);
                b.setNegativeButton("Cancel", null);
                b.setPositiveButton("Use", (d1,w1)->{
                    float wpx = Math.max(1, widthSeek.getProgress());
                    overlay.setEraserWidth(wpx);
                    overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.ERASER);
                });
                b.show();
                return true;
            });
            btnUndo.setOnClickListener(v -> overlay.undo());
            btnRedo.setOnClickListener(v -> overlay.redo());

            btnClear.setOnClickListener(v -> {
                // Ask user to confirm clearing all annotations
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Clear annotations")
                        .setMessage("Are you sure you want to clear all annotations?")
                        .setPositiveButton("Yes", (d,w) -> {
                            // Clear current overlay content
                            overlay.clear();
                            // Also replace current active annotation layer with a clear one (transparent)
                            new Thread(() -> {
                                try {
                                    AppDatabase db = AppDatabase.getDatabase(requireContext().getApplicationContext());
                                    PageLayerEntity layer = db.pageLayerDao().getActiveAnnotationLayer(currentSheetId, currentPage);

                                    int targetW = Math.max(1, overlay.getWidth());
                                    int targetH = Math.max(1, overlay.getHeight());
                                    try {
                                        android.graphics.drawable.Drawable d2 = photoView.getDrawable();
                                        if (d2 != null && d2.getIntrinsicWidth() > 0 && d2.getIntrinsicHeight() > 0) {
                                            targetW = d2.getIntrinsicWidth();
                                            targetH = d2.getIntrinsicHeight();
                                        }
                                    } catch (Throwable ignored) {}

                                    Bitmap empty = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888); // transparent by default

                                    File dir = new File(requireContext().getFilesDir(), "annotations");
                                    if (!dir.exists()) dir.mkdirs();

                                    File outFile;
                                    if (layer != null && layer.getLayerImagePath() != null && !layer.getLayerImagePath().isEmpty()) {
                                        outFile = new File(layer.getLayerImagePath());
                                    } else {
                                        String fileName = "annot_sheet_" + currentSheetId + "_page_" + currentPage + "_" + System.currentTimeMillis() + ".png";
                                        outFile = new File(dir, fileName);
                                    }

                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                                    empty.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                    fos.flush();
                                    fos.close();
                                    empty.recycle();

                                    long now = System.currentTimeMillis();
                                    if (layer == null) {
                                        int maxOrder = db.pageLayerDao().getMaxOrderIndex(currentSheetId, currentPage);
                                        layer = new PageLayerEntity(currentSheetId, currentPage, "Annotations", "ANNOTATION");
                                        layer.setActive(true);
                                        layer.setOrderIndex(maxOrder + 1);
                                        layer.setLayerImagePath(outFile.getAbsolutePath());
                                        layer.setCreatedAt(now);
                                        layer.setModifiedAt(now);
                                        db.pageLayerDao().insert(layer);
                                    } else {
                                        layer.setLayerImagePath(outFile.getAbsolutePath());
                                        layer.setModifiedAt(now);
                                        db.pageLayerDao().update(layer);
                                    }

                                    runOnUiThread(this::refreshPage);
                                } catch (Throwable t) {
                                    Log.e(TAG, "Failed to clear annotation layer", t);
                                }
                            }).start();
                        })
                        .setNegativeButton("No", null)
                        .show();
            });

            // Text edit callback (prefilled for editing existing boxes)
            overlay.setOnRequestEditTextListener(textBox -> {
                final android.widget.LinearLayout container = new android.widget.LinearLayout(requireContext());
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                int pad = (int)(8 * getResources().getDisplayMetrics().density);
                container.setPadding(pad, pad, pad, pad);

                final android.widget.EditText input = new android.widget.EditText(requireContext());
                input.setHint("Enter text");
                input.setText(textBox.text != null ? textBox.text : "");
                container.addView(input);

                final android.widget.CheckBox cbBold = new android.widget.CheckBox(requireContext());
                cbBold.setText("Bold");
                cbBold.setChecked(textBox.bold);
                container.addView(cbBold);

                final android.widget.SeekBar sizeSeek = new android.widget.SeekBar(requireContext());
                sizeSeek.setMax(72);
                int currentSp = (int)Math.max(8, textBox.textSizeSp);
                sizeSeek.setProgress(currentSp);
                container.addView(sizeSeek);

                // Color picker for text color
                final int[] pickedTextColor = new int[]{ textBox.paint.getColor() };
                final com.skydoves.colorpickerview.ColorPickerView textColorPicker = new com.skydoves.colorpickerview.ColorPickerView.Builder(requireContext()).build();
                // Use the library's default HSV palette per official docs (no explicit call needed)
                textColorPicker.setInitialColor(pickedTextColor[0]);
                textColorPicker.setColorListener((com.skydoves.colorpickerview.listeners.ColorEnvelopeListener) (envelope, fromUser) -> {
                    pickedTextColor[0] = envelope.getColor();
                });
                int cpH3 = (int)(220 * getResources().getDisplayMetrics().density);
                container.addView(textColorPicker, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, cpH3));

                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Text Options")
                        .setView(container)
                        .setPositiveButton("OK", (dlg, w) -> {
                            textBox.text = input.getText().toString();
                            textBox.bold = cbBold.isChecked();
                            int color = pickedTextColor[0];
                            textBox.paint.setColor(color);
                            float sz = Math.max(8, sizeSeek.getProgress());
                            textBox.textSizeSp = sz;
                            textBox.paint.setTextSize(sz * getResources().getDisplayMetrics().scaledDensity);
                            textBox.paint.setFakeBoldText(textBox.bold);
                            overlay.invalidate();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            btnSave.setOnClickListener(v -> {
                saveAnnotations(overlay);
            });
            btnClose.setOnClickListener(v -> {
                overlay.setVisibility(View.GONE);
                toolbar.setVisibility(View.GONE);
                overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.NONE);
                if (photoView != null) photoView.setZoomable(true);
            });
        }

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

        // Remove previous settings observer as well
        if (pageSettingsLiveData != null) {
            try {
                pageSettingsLiveData.removeObservers(vlo);
            } catch (Exception ignored) {}
        }

        // Get active layers for this page and observe
        activeLayersLiveData = db.pageLayerDao().getActiveLayersForPage(sheetId, pageNumber);
        activeLayersLiveData.observe(vlo, layers -> {
            // Apply on background thread regardless of layers list emptiness so PageSettings are honored
            new Thread(() -> {
                try {
                    // Start from base
                    Bitmap working = baseBitmap;
                    // Apply virtual page settings first (rotation -> crop -> adjustments)
                    try {
                        AppDatabase adb = AppDatabase.getDatabase(requireContext().getApplicationContext());
                        PageSettingsEntity settings = adb.pageSettingsDao().getByPage(sheetId, pageNumber);
                        if (settings != null) {
                            if (settings.getRotation() != 0f) {
                                working = applyRotation(working, settings.getRotation());
                            }
                            if (!(Math.abs(settings.getCropLeft()) < 1e-3 && Math.abs(settings.getCropTop()) < 1e-3 && Math.abs(settings.getCropRight() - 1f) < 1e-3 && Math.abs(settings.getCropBottom() - 1f) < 1e-3)) {
                                // Build a temporary PageLayerEntity-like wrapper to reuse applyCrop
                                PageLayerEntity temp = new PageLayerEntity(sheetId, pageNumber, "__virtual_crop__", "CROP");
                                temp.setCropLeft(settings.getCropLeft());
                                temp.setCropTop(settings.getCropTop());
                                temp.setCropRight(settings.getCropRight());
                                temp.setCropBottom(settings.getCropBottom());
                                working = applyCrop(working, temp);
                            }
                            if (settings.getBrightness() != 0f || Math.abs(settings.getContrast() - 1f) > 1e-3) {
                                applyAdjustmentsInPlace(working, settings.getBrightness(), settings.getContrast());
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to apply page settings", t);
                    }

                    // Then apply annotation layers (active, non-CROP) if any
                    Bitmap compositeBitmap = (layers != null && !layers.isEmpty()) ? applyLayers(working, layers) : working;
                    runOnUiThread(() -> displayBitmap(compositeBitmap));
                } catch (Exception e) {
                    Log.e(TAG, "Error applying layers", e);
                    runOnUiThread(() -> displayBitmap(baseBitmap));
                }
            }).start();
        });

        // Also observe virtual page settings so that main screen updates immediately after CropActivity saves/restores
        pageSettingsLiveData = db.pageSettingsDao().observeByPage(sheetId, pageNumber);
        pageSettingsLiveData.observe(vlo, settings -> {
            new Thread(() -> {
                try {
                    Bitmap working = baseBitmap;
                    if (settings != null) {
                        if (settings.getRotation() != 0f) {
                            working = applyRotation(working, settings.getRotation());
                        }
                        if (!(Math.abs(settings.getCropLeft()) < 1e-3 && Math.abs(settings.getCropTop()) < 1e-3 && Math.abs(settings.getCropRight() - 1f) < 1e-3 && Math.abs(settings.getCropBottom() - 1f) < 1e-3)) {
                            PageLayerEntity temp = new PageLayerEntity(sheetId, pageNumber, "__virtual_crop__", "CROP");
                            temp.setCropLeft(settings.getCropLeft());
                            temp.setCropTop(settings.getCropTop());
                            temp.setCropRight(settings.getCropRight());
                            temp.setCropBottom(settings.getCropBottom());
                            working = applyCrop(working, temp);
                        }
                        if (settings.getBrightness() != 0f || Math.abs(settings.getContrast() - 1f) > 1e-3) {
                            applyAdjustmentsInPlace(working, settings.getBrightness(), settings.getContrast());
                        }
                    }
                    // Overlay current active layers if available
                    List<PageLayerEntity> curLayers = activeLayersLiveData != null ? activeLayersLiveData.getValue() : null;
                    Bitmap compositeBitmap = (curLayers != null && !curLayers.isEmpty()) ? applyLayers(working, curLayers) : working;
                    runOnUiThread(() -> displayBitmap(compositeBitmap));
                } catch (Exception e) {
                    Log.e(TAG, "Error applying settings observer", e);
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
        Canvas canvas = new Canvas(working);

        for (PageLayerEntity layer : layers) {
            if (!layer.isActive()) continue;

            try {
                // Apply rotation BEFORE crop to match CropActivity preview behavior
                if (layer.hasRotation()) {
                    Bitmap newBmp = applyRotation(working, layer.getRotation());
                    if (working != baseBitmap && working != newBmp && !working.isRecycled()) {
                        working.recycle();
                    }
                    working = newBmp;
                    canvas.setBitmap(working);
                }

                // Apply crop (creates a new bitmap). Recycle previous to save memory.
                if (layer.hasCrop()) {
                    Bitmap newBmp = applyCrop(working, layer);
                    if (working != baseBitmap && working != newBmp && !working.isRecycled()) {
                        working.recycle();
                    }
                    working = newBmp;
                    canvas.setBitmap(working);
                }

                // Apply adjustments in-place to avoid extra allocation
                if (layer.hasAdjustments()) {
                    applyAdjustmentsInPlace(working, layer.getBrightness(), layer.getContrast());
                }

                // Composite annotation bitmap if provided, unless suppressed while in annotation mode
                if (suppressAnnotationComposite && "ANNOTATION".equalsIgnoreCase(layer.getLayerType())) {
                    // Skip drawing this layer while annotating so on-screen undo/redo reflects immediately
                } else {
                    String path = layer.getLayerImagePath();
                    if (path != null && !path.isEmpty()) {
                        try {
                            android.graphics.Bitmap overlay = android.graphics.BitmapFactory.decodeFile(path);
                            if (overlay != null) {
                                if (overlay.getWidth() != working.getWidth() || overlay.getHeight() != working.getHeight()) {
                                    // Scale overlay to current working size
                                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(overlay, working.getWidth(), working.getHeight(), true);
                                    canvas.drawBitmap(scaled, 0, 0, null);
                                    if (scaled != overlay) scaled.recycle();
                                    overlay.recycle();
                                } else {
                                    canvas.drawBitmap(overlay, 0, 0, null);
                                    overlay.recycle();
                                }
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Failed to overlay annotation bitmap: " + path, t);
                        }
                    }
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

        // Double-tap in the center area enters annotation mode
        photoView.setOnDoubleTapListener(new android.view.GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                return false; // let PhotoView handle single taps
            }
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                View v = photoView;
                if (v == null) return false;
                int w = v.getWidth();
                int h = v.getHeight();
                if (w <= 0 || h <= 0) return false;
                float x = e.getX();
                float y = e.getY();
                // Define center region as middle third of the view
                float left = w / 3f;
                float right = w * 2f / 3f;
                float top = h / 3f;
                float bottom = h * 2f / 3f;
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    enterAnnotationMode();
                    return true; // consume to prevent default double-tap zoom
                }
                return false; // allow default behavior elsewhere
            }
            @Override
            public boolean onDoubleTapEvent(android.view.MotionEvent e) {
                return false;
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

    private void loadPreviousOpsIntoOverlay() {
        com.example.staffpad.views.AnnotationOverlayView overlay = annotationOverlay;
        if (overlay == null) return;
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(requireContext().getApplicationContext());
                PageLayerEntity layer = db.pageLayerDao().getActiveAnnotationLayer(currentSheetId, currentPage);
                if (layer == null || layer.getLayerImagePath() == null) return;
                File jsonFile = new File(layer.getLayerImagePath().replace(".png", ".json"));
                if (!jsonFile.exists()) return;
                String content = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
                org.json.JSONObject root = new org.json.JSONObject(content);
                org.json.JSONArray arr = root.optJSONArray("ops");
                if (arr == null) return;
                java.util.List<com.example.staffpad.views.AnnotationOverlayView.AnnotationItem> list = new java.util.ArrayList<>();
                for (int i=0;i<arr.length();i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    String type = obj.optString("type","unknown");
                    if ("stroke".equals(type)) {
                        int color = obj.optInt("color", Color.BLACK);
                        int alpha = obj.optInt("alpha", 255);
                        float width = (float)obj.optDouble("width", 6.0);
                        com.example.staffpad.views.AnnotationOverlayView.Stroke s = new com.example.staffpad.views.AnnotationOverlayView.Stroke(color, width, alpha);
                        org.json.JSONArray pts = obj.optJSONArray("points");
                        if (pts != null && pts.length() > 0) {
                            for (int p=0;p<pts.length();p++) {
                                org.json.JSONArray pp = pts.getJSONArray(p);
                                float px = (float) pp.getDouble(0);
                                float py = (float) pp.getDouble(1);
                                s.addPoint(px, py, p==0);
                            }
                        }
                        list.add(s);
                    } else if ("erase".equals(type)) {
                        float width = (float)obj.optDouble("width", 20.0);
                        com.example.staffpad.views.AnnotationOverlayView.EraseStroke es = new com.example.staffpad.views.AnnotationOverlayView.EraseStroke(width);
                        org.json.JSONArray pts = obj.optJSONArray("points");
                        if (pts != null && pts.length() > 0) {
                            for (int p=0;p<pts.length();p++) {
                                org.json.JSONArray pp = pts.getJSONArray(p);
                                float px = (float) pp.getDouble(0);
                                float py = (float) pp.getDouble(1);
                                es.addPoint(px, py, p==0);
                            }
                        }
                        list.add(es);
                    } else if ("text".equals(type)) {
                        String text = obj.optString("text", "");
                        float x = (float)obj.optDouble("x", 0.0);
                        float y = (float)obj.optDouble("y", 0.0);
                        float sizePx = (float)obj.optDouble("size", 16.0);
                        int color = obj.optInt("color", Color.BLACK);
                        boolean bold = obj.optBoolean("bold", false);
                        com.example.staffpad.views.AnnotationOverlayView.TextBox t = new com.example.staffpad.views.AnnotationOverlayView.TextBox(x, y, color, 16f, bold);
                        t.text = text;
                        t.paint.setColor(color);
                        t.paint.setTextSize(sizePx);
                        t.paint.setFakeBoldText(bold);
                        t.textSizeSp = sizePx / getResources().getDisplayMetrics().scaledDensity;
                        list.add(t);
                    }
                }
                runOnUiThread(() -> overlay.setItemsFromHistory(list));
            } catch (Throwable t) {
                Log.w(TAG, "Failed to load previous ops", t);
            }
        }).start();
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

    // Public controls for toolbox Annotate button
    public void enterAnnotationMode() {
        // While annotating, suppress compositing of persisted annotation layer to enable cross-session undo of last 20 steps
        suppressAnnotationComposite = true;
        View toolbar = annotationToolbar != null ? annotationToolbar : (getView() != null ? getView().findViewById(R.id.annotation_toolbar) : null);
        com.example.staffpad.views.AnnotationOverlayView overlay = annotationOverlay != null ? annotationOverlay : (getView() != null ? getView().findViewById(R.id.annotation_overlay) : null);
        if (toolbar != null && overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            toolbar.setVisibility(View.VISIBLE);
            overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.PEN);
            if (photoView != null) photoView.setZoomable(false);
            // Redraw base page without persisted annotations
            refreshPage();
            // Load last 20 ops from previous session if available
            loadPreviousOpsIntoOverlay();
        }
        if (penPresetList != null) penPresetList.setVisibility(View.VISIBLE);
    }

    public void exitAnnotationMode() {
        suppressAnnotationComposite = false;
        View toolbar = annotationToolbar != null ? annotationToolbar : (getView() != null ? getView().findViewById(R.id.annotation_toolbar) : null);
        com.example.staffpad.views.AnnotationOverlayView overlay = annotationOverlay != null ? annotationOverlay : (getView() != null ? getView().findViewById(R.id.annotation_overlay) : null);
        if (toolbar != null && overlay != null) {
            overlay.setVisibility(View.GONE);
            toolbar.setVisibility(View.GONE);
            overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.NONE);
            if (photoView != null) photoView.setZoomable(true);
            // Redraw with composited annotation layer visible again
            refreshPage();
        }
        if (penPresetList != null) penPresetList.setVisibility(View.GONE);
    }

    private void seedDefaultPenPresets() {
        penPresets.clear();
        penPresets.add(new PenPreset("Black", Color.BLACK, 4f, 255));
        penPresets.add(new PenPreset("Red", Color.RED, 6f, 255));
        penPresets.add(new PenPreset("Blue", Color.BLUE, 6f, 255));
        penPresets.add(new PenPreset("Yellow HL", Color.YELLOW, 10f, (int)(255*0.3f)));
    }

    private void loadPenPresetsFromPrefs() {
        try {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("StaffPadPrefs", android.content.Context.MODE_PRIVATE);
            String json = prefs.getString("pen_presets", null);
            penPresets.clear();
            // Always include defaults
            seedDefaultPenPresets();
            if (json != null) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i=0;i<arr.length();i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    penPresets.add(new PenPreset(
                            o.optString("name","Pen"),
                            o.optInt("color", Color.BLACK),
                            (float)o.optDouble("width", 6.0),
                            o.optInt("alpha", 255)
                    ));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed loading pen presets", t);
            penPresets.clear();
            seedDefaultPenPresets();
        }
    }

    private void savePenPresetsToPrefs() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (PenPreset p : penPresets) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", p.name);
                o.put("color", p.color);
                o.put("width", p.widthPx);
                o.put("alpha", p.alpha);
                arr.put(o);
            }
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("StaffPadPrefs", android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("pen_presets", arr.toString()).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed saving pen presets", t);
        }
    }

    private void showEditPresetDialog(int index) {
        if (index < 0 || index >= penPresets.size()) return;
        PenPreset preset = penPresets.get(index);
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
        b.setTitle("Edit Preset");
        final android.widget.LinearLayout container = new android.widget.LinearLayout(requireContext());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(12 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        // Preview area
        final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeCap(Paint.Cap.ROUND);
        previewPaint.setColor(preset.color);
        previewPaint.setStrokeWidth(Math.max(1f, preset.widthPx));
        previewPaint.setAlpha(preset.alpha);

        final View preview = new View(requireContext()) {
            @Override protected void onDraw(Canvas c) {
                super.onDraw(c);
                int w = getWidth();
                int h = getHeight();
                // light background
                c.drawColor(0xFFEFEFEF);
                int cy = h / 2;
                int left = pad;
                int right = Math.max(left + 1, w - pad);
                c.drawLine(left, cy, right, cy, previewPaint);
            }
        };
        int pvH = (int)(64 * getResources().getDisplayMetrics().density);
        container.addView(preview, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, pvH));

        final android.widget.EditText nameEt = new android.widget.EditText(requireContext());
        nameEt.setHint("Name");
        nameEt.setText(preset.name);
        container.addView(nameEt);

        final android.widget.SeekBar widthSeek = new android.widget.SeekBar(requireContext());
        widthSeek.setMax(80);
        widthSeek.setProgress((int)Math.max(1, preset.widthPx));
        widthSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                previewPaint.setStrokeWidth(Math.max(1, progress));
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        container.addView(widthSeek);

        // Opacity slider for preset alpha (0-100%)
        final android.widget.TextView alphaLabel = new android.widget.TextView(requireContext());
        alphaLabel.setText("Opacity");
        container.addView(alphaLabel);
        final android.widget.SeekBar alphaSeek = new android.widget.SeekBar(requireContext());
        alphaSeek.setMax(100);
        int initialAlphaPct = (int) Math.round((preset.alpha / 255.0) * 100.0);
        alphaSeek.setProgress(initialAlphaPct);
        alphaSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int a = (int) Math.round(progress * 2.55);
                previewPaint.setAlpha(a);
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        container.addView(alphaSeek);

        // Color picker for preset color
        final int[] pickedColor = new int[]{ preset.color };
        final com.skydoves.colorpickerview.ColorPickerView colorPicker = new com.skydoves.colorpickerview.ColorPickerView.Builder(requireContext()).build();
        // Use the library's default HSV palette per official docs (no explicit call needed)
        colorPicker.setInitialColor(preset.color);
        colorPicker.setColorListener((com.skydoves.colorpickerview.listeners.ColorEnvelopeListener) (envelope, fromUser) -> {
            pickedColor[0] = envelope.getColor();
            previewPaint.setColor(pickedColor[0]);
            preview.invalidate();
        });
        int cpH2 = (int)(220 * getResources().getDisplayMetrics().density);
        container.addView(colorPicker, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cpH2));

        b.setView(container);
        b.setNegativeButton("Cancel", null);
        b.setPositiveButton("Save", (d,w)->{
            preset.name = nameEt.getText().toString();
            preset.widthPx = Math.max(1, widthSeek.getProgress());
            preset.color = pickedColor[0];
            preset.alpha = (int) Math.round(alphaSeek.getProgress() * 2.55);
            savePenPresetsToPrefs();
            if (penPresetAdapter != null) penPresetAdapter.notifyItemChanged(index);
            // Immediately use this edited pen after saving changes
            if (annotationOverlay != null) {
                annotationOverlay.setPen(preset.color, preset.widthPx, preset.alpha);
                annotationOverlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.PEN);
            }
        });
        b.show();
    }

    private void saveAnnotations(com.example.staffpad.views.AnnotationOverlayView overlay) {
        try {
            if (overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
                Log.w(TAG, "Overlay has zero size; skipping save");
                return;
            }
            // Export current overlay as bitmap at overlay size
            Bitmap overlayBmp = overlay.exportToBitmap(overlay.getWidth(), overlay.getHeight());
            // Capture snapshot of items BEFORE clearing overlay (used for sidecar JSON)
            final java.util.List<com.example.staffpad.views.AnnotationOverlayView.AnnotationItem> opsSnapshot = overlay.getItemsSnapshot();

            // Determine base image size from the PhotoView drawable if available; otherwise use overlay size
            int targetW = overlayBmp.getWidth();
            int targetH = overlayBmp.getHeight();
            try {
                android.graphics.drawable.Drawable d = photoView.getDrawable();
                if (d != null && d.getIntrinsicWidth() > 0 && d.getIntrinsicHeight() > 0) {
                    targetW = d.getIntrinsicWidth();
                    targetH = d.getIntrinsicHeight();
                }
            } catch (Throwable ignored) {}

            Bitmap finalBmp;
            if (overlayBmp.getWidth() != targetW || overlayBmp.getHeight() != targetH) {
                finalBmp = Bitmap.createScaledBitmap(overlayBmp, targetW, targetH, true);
                if (finalBmp != overlayBmp) overlayBmp.recycle();
            } else {
                finalBmp = overlayBmp;
            }

            // Immediately clear overlay on UI thread to avoid on-screen duplication
            runOnUiThread(() -> {
                try {
                    overlay.clear();
                } catch (Throwable ignored) {}
            });

            // Decide target file based on existing active annotation layer
            Context appCtx = requireContext().getApplicationContext();
            final File[] outFileHolder = new File[1];
            final long nowTs = System.currentTimeMillis();
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(appCtx);
                    PageLayerEntity existing = db.pageLayerDao().getActiveAnnotationLayer(currentSheetId, currentPage);

                    File dir = new File(requireContext().getFilesDir(), "annotations");
                    if (!dir.exists()) dir.mkdirs();

                    File outFile;
                    if (existing != null && existing.getLayerImagePath() != null && !existing.getLayerImagePath().isEmpty()) {
                        outFile = new File(existing.getLayerImagePath());
                    } else {
                        String fileName = "annot_sheet_" + currentSheetId + "_page_" + currentPage + "_" + nowTs + ".png";
                        outFile = new File(dir, fileName);
                    }

                    // Overwrite the annotation layer with ONLY the current overlay content (respecting undo), no compositing
                    try {
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        finalBmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush(); fos.close();
                    } finally {
                        finalBmp.recycle();
                    }

                    // Save sidecar ops JSON: write ONLY the current snapshot (trim to last 20)
                    try {
                        java.util.List<com.example.staffpad.views.AnnotationOverlayView.AnnotationItem> snapshot = opsSnapshot;
                        org.json.JSONArray trimmed = new org.json.JSONArray();
                        int startIdx = Math.max(0, snapshot.size() - 20);
                        for (int i = startIdx; i < snapshot.size(); i++) {
                            com.example.staffpad.views.AnnotationOverlayView.AnnotationItem it = snapshot.get(i);
                            org.json.JSONObject obj = new org.json.JSONObject();
                            if (it instanceof com.example.staffpad.views.AnnotationOverlayView.Stroke) {
                                com.example.staffpad.views.AnnotationOverlayView.Stroke s = (com.example.staffpad.views.AnnotationOverlayView.Stroke) it;
                                obj.put("type", "stroke");
                                obj.put("color", s.paint.getColor());
                                obj.put("alpha", s.paint.getAlpha());
                                obj.put("width", s.paint.getStrokeWidth());
                                org.json.JSONArray pts = new org.json.JSONArray();
                                for (float[] p : s.points) {
                                    org.json.JSONArray pp = new org.json.JSONArray();
                                    pp.put(p[0]); pp.put(p[1]);
                                    pts.put(pp);
                                }
                                obj.put("points", pts);
                            } else if (it instanceof com.example.staffpad.views.AnnotationOverlayView.EraseStroke) {
                                com.example.staffpad.views.AnnotationOverlayView.EraseStroke es = (com.example.staffpad.views.AnnotationOverlayView.EraseStroke) it;
                                obj.put("type", "erase");
                                obj.put("width", es.paint.getStrokeWidth());
                                org.json.JSONArray pts = new org.json.JSONArray();
                                for (float[] p : es.points) {
                                    org.json.JSONArray pp = new org.json.JSONArray();
                                    pp.put(p[0]); pp.put(p[1]);
                                    pts.put(pp);
                                }
                                obj.put("points", pts);
                            } else if (it instanceof com.example.staffpad.views.AnnotationOverlayView.TextBox) {
                                com.example.staffpad.views.AnnotationOverlayView.TextBox t = (com.example.staffpad.views.AnnotationOverlayView.TextBox) it;
                                obj.put("type", "text");
                                obj.put("text", t.text);
                                obj.put("x", t.x);
                                obj.put("y", t.y);
                                obj.put("size", t.paint.getTextSize());
                                obj.put("color", t.paint.getColor());
                                obj.put("bold", t.bold);
                            } else {
                                obj.put("type", "unknown");
                            }
                            trimmed.put(obj);
                        }
                        File jsonFile = new File(dir, outFile.getName().replace(".png", ".json"));
                        org.json.JSONObject root = new org.json.JSONObject();
                        root.put("ops", trimmed);
                        java.io.FileWriter fw = new java.io.FileWriter(jsonFile);
                        fw.write(root.toString()); fw.flush(); fw.close();
                    } catch (Throwable t) { Log.w(TAG, "Failed writing ops JSON", t);}            

                    // Persist the layer (update or insert)
                    if (existing == null) {
                        int maxOrder = db.pageLayerDao().getMaxOrderIndex(currentSheetId, currentPage);
                        PageLayerEntity layer = new PageLayerEntity(currentSheetId, currentPage, "Annotations", "ANNOTATION");
                        layer.setActive(true);
                        layer.setOrderIndex(maxOrder + 1);
                        layer.setLayerImagePath(outFile.getAbsolutePath());
                        layer.setModifiedAt(nowTs);
                        layer.setCreatedAt(nowTs);
                        db.pageLayerDao().insert(layer);
                    } else {
                        existing.setLayerImagePath(outFile.getAbsolutePath());
                        existing.setModifiedAt(nowTs);
                        db.pageLayerDao().update(existing);
                    }

                    // After saving, exit annotation mode so the saved layer composites on the main screen immediately
                    runOnUiThread(() -> {
                        exitAnnotationMode();
                        // Extra safety: force a follow-up refresh shortly after to avoid any race with file/DB observers
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> refreshPage(), 150);
                    });
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to save annotation layer", t);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error saving annotations", e);
        }
    }

}
