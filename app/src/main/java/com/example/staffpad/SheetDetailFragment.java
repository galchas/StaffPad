package com.example.staffpad;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.LiveData;

import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.viewmodel.SheetViewModel;
import com.example.staffpad.views.AnnotationOverlayView;
import com.github.chrisbanes.photoview.OnPhotoTapListener;
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
import android.media.MediaPlayer;
import android.net.Uri;
import com.google.android.material.slider.Slider;
import com.example.staffpad.database.AudioEntity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.app.ActivityManager;
import android.content.Context;

public class SheetDetailFragment extends Fragment {
    // Keep a weak reference to the latest instance so other UI parts can request a pause
    private static java.lang.ref.WeakReference<SheetDetailFragment> sLastInstanceRef = new java.lang.ref.WeakReference<>(null);

    public static void requestPausePlayback() {
        SheetDetailFragment inst = sLastInstanceRef.get();
        if (inst != null) {
            try { inst.stopPlayerIfPlaying(); } catch (Throwable ignore) {}
        }
    }
    // --- Audio/Video bottom player state ---
    private static class Track {
        enum Type { FILE, YOUTUBE }
        Type type;
        String title;
        String uri; // for FILE: content/file uri; for YOUTUBE: full url
        long durationMs; // known for FILE if available
        Track(Type type, String title, String uri, long durationMs) {
            this.type = type; this.title = title; this.uri = uri; this.durationMs = durationMs;
        }
    }
    private final List<Track> playlist = new ArrayList<>();
    private int currentTrackIndex = 0;
    private android.media.MediaPlayer mediaPlayer;
    private com.google.android.material.slider.Slider timeSlider;
    private com.google.android.material.slider.Slider volumeSlider;
    private ImageView btnPrev, btnNext, btnRestart, btnPlay, btnPause;
    private TextView trackNameView, trackTimeView;
    private android.os.Handler progressHandler;
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            updateProgressUi();
            if (progressHandler != null) progressHandler.postDelayed(this, 500);
        }
    };
    private View audioArtwork;
    private View youtubeLoading;
    private boolean restartArmed = false; // for double-click behavior on restart
    // Playback readiness flags to handle Play pressed before player is ready
    private boolean mpPrepared = false;
    private boolean mpPendingPlay = false;
    private boolean ytPendingPlay = false;
    // Bottom player dialog container, toggled with center single tap
    private View bottomPlayerContainer;
    private com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView youTubePlayerView;
    private com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayer;
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
                    stopPlayerIfPlaying();
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
            sLastInstanceRef = new java.lang.ref.WeakReference<>(this);
        super.onViewCreated(view, savedInstanceState);
        photoView = view.findViewById(R.id.photo_view);
        // Bottom player is now lazy-inflated via ViewStub; do not touch until needed
        bottomPlayerContainer = view.findViewById(R.id.bottom_player_container); // may be null until inflated
        youTubePlayerView = null; // will be assigned upon inflation
        // Ensure navigation gestures are attached as early as possible
        setupPageNavigation();

        // Annotation UI setup (no standalone entry button; controlled via toolbox)
        annotationToolbar = view.findViewById(R.id.annotation_toolbar);
        annotationOverlay = view.findViewById(R.id.annotation_overlay);
        View toolbar = annotationToolbar;
        AnnotationOverlayView overlay = annotationOverlay;
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

            btnText.setOnClickListener(v -> {
                stopPlayerIfPlaying();
                overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.TEXT);
            });
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

            if (btnEraser != null) {
                btnEraser.setOnClickListener(v -> {
                    stopPlayerIfPlaying();
                    overlay.setMode(com.example.staffpad.views.AnnotationOverlayView.ToolMode.ERASER);
                });
            }

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
            // Tell ViewModel which sheet is active so audio list can be observed
            try { sheetViewModel.selectSheet(sheetId); } catch (Throwable ignore) {}

            // Observe audio attachments for this sheet
            try {
                sheetViewModel.getAudioFilesForSelectedSheet().observe(getViewLifecycleOwner(), list -> {
                    buildPlaylistFromAudioEntities(list);
                    if (!playlist.isEmpty()) {
                        inflateBottomPlayerIfNeeded();
                        loadTrack(0, false);
                        showBottomDialog();
                    } else {
                        hideBottomDialog();
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Failed to observe audio files", t);
            }

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
        // Stop playback when switching to another sheet
        stopPlayerIfPlaying();
        releaseMediaPlayer();
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
        // Clear static ref if fragment is going to background? keep until destroyed
        super.onPause();

        // Save the current page when leaving the fragment
        if (currentSheetId != -1) {
            preferencesHelper.saveLastViewedPage(currentSheetId, currentPage);
            Log.d(TAG, "onPause: Saved last viewed page: " + currentPage);
        }
        // Pause playback when user leaves activity (stop condition)
        stopPlayerIfPlaying();
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


    private void toggleUiChrome() {
        try {
            // Toggle top app toolbar in activity
            androidx.fragment.app.FragmentActivity act = requireActivity();
            View toolbar = act.findViewById(R.id.app_toolbar);
            if (toolbar != null) {
                toolbar.setVisibility(toolbar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "toggleUiChrome: could not toggle app toolbar", t);
        }
        // Toggle bottom player lazily
        if (!isBottomDialogVisible()) {
            inflateBottomPlayerIfNeeded();
            showBottomDialog();
        } else {
            hideBottomDialog();
        }
    }

    private void inflateBottomPlayerIfNeeded() {
        if (bottomPlayerContainer != null) return; // already inflated
        try {
            View root = getView();
            if (root == null) return;
            View stubView = root.findViewById(R.id.bottom_player_stub);
            if (stubView instanceof android.view.ViewStub) {
                android.view.View inflated = ((android.view.ViewStub) stubView).inflate();
                bottomPlayerContainer = inflated; // id becomes bottom_player_container

                // Bind views
                btnPrev = inflated.findViewById(R.id.btnPrevTrack);
                btnNext = inflated.findViewById(R.id.btnNextTrack);
                btnRestart = inflated.findViewById(R.id.btnRestart);
                btnPlay = inflated.findViewById(R.id.btnPlay);
                btnPause = inflated.findViewById(R.id.btnPause);
                trackNameView = inflated.findViewById(R.id.trackName);
                trackTimeView = inflated.findViewById(R.id.trackTime);
                timeSlider = inflated.findViewById(R.id.sliderTime);
                volumeSlider = inflated.findViewById(R.id.volumeSlider);
                audioArtwork = inflated.findViewById(R.id.audioArtwork);
                youtubeLoading = inflated.findViewById(R.id.youtubeLoading);

                // Wire YouTube player
                youTubePlayerView = inflated.findViewById(R.id.youTunePlayer);
                if (youTubePlayerView != null) {
                    youTubePlayerView.getYouTubePlayerWhenReady(player -> youTubePlayer = player);
                    youTubePlayerView.addYouTubePlayerListener(new com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener() {
                        private float ytDuration = 0f;
                        private float ytCurrent = 0f;
                        @Override
                        public void onReady(com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayerInstance) {
                            youTubePlayer = youTubePlayerInstance;
                            // Ensure current YouTube track is loaded when player becomes ready
                            Track current = getCurrentTrack();
                            if (current != null && current.type == Track.Type.YOUTUBE) {
                                String videoId = extractYouTubeId(current.uri);
                                if (videoId != null) {
                                    try {
                                        youTubePlayer.cueVideo(videoId, 0f);
                                        if (ytPendingPlay) {
                                            ytPendingPlay = false;
                                            youTubePlayer.play();
                                        }
                                    } catch (Throwable ignore) {}
                                }
                            }
                            // Hide loading once player is ready (will also be handled by state changes)
                            if (youtubeLoading != null) youtubeLoading.setVisibility(View.GONE);
                        }
                        @Override
                        public void onCurrentSecond(com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayer, float second) {
                            ytCurrent = second;
                            updateYouTubeProgress(ytCurrent, ytDuration);
                        }
                        @Override
                        public void onVideoDuration(com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayer, float duration) {
                            ytDuration = duration;
                            updateYouTubeProgress(ytCurrent, ytDuration);
                        }
                        @Override
                        public void onStateChange(com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayer,
                                                  com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState state) {
                            // Toggle loading indicator based on buffering/playing states
                            if (youtubeLoading == null) return;
                            switch (state) {
                                case BUFFERING:
                                    youtubeLoading.setVisibility(View.VISIBLE);
                                    break;
                                case PLAYING:
                                case PAUSED:
                                case ENDED:
                                case VIDEO_CUED:
                                    youtubeLoading.setVisibility(View.GONE);
                                    break;
                                default:
                                    break;
                            }
                        }
                        @Override
                        public void onError(com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer youTubePlayer,
                                            com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError error) {
                            if (youtubeLoading != null) youtubeLoading.setVisibility(View.GONE);
                        }
                    });
                }

                // Listeners
                if (btnPrev != null) btnPrev.setOnClickListener(v -> prevTrack());
                if (btnNext != null) btnNext.setOnClickListener(v -> nextTrack());
                if (btnPlay != null) btnPlay.setOnClickListener(v -> play());
                if (btnPause != null) btnPause.setOnClickListener(v -> pause());
                if (btnRestart != null) btnRestart.setOnClickListener(v -> onRestartClicked());

                if (timeSlider != null) {
                    timeSlider.setValueFrom(0f);
                    timeSlider.addOnChangeListener((slider, value, fromUser) -> {
                        // Only seek on user changes at touch end to avoid jitter; handled below
                    });
                    timeSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                        @Override public void onStartTrackingTouch(@NonNull Slider slider) {}
                        @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                            seekTo((long) slider.getValue());
                        }
                    });
                }
                if (volumeSlider != null) {
                    volumeSlider.setValueFrom(0f);
                    volumeSlider.setValueTo(100f);
                    volumeSlider.setValue(100f);
                    volumeSlider.addOnChangeListener((s, value, fromUser) -> setVolume(value / 100f));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "inflateBottomPlayerIfNeeded: failed to inflate bottom player", t);
        }
    }

    private void updateYouTubeProgress(float currentSec, float durationSec) {
        if (timeSlider == null || trackTimeView == null) return;
        long curMs = (long)(currentSec * 1000);
        long durMs = (long)(durationSec * 1000);
        timeSlider.setValueTo(Math.max(1, durMs));
        timeSlider.setValue(curMs);
        trackTimeView.setText(formatTime(curMs) + " / " + formatTime(durMs));
    }

    private void setVolume(float v) {
        v = Math.max(0f, Math.min(1f, v));
        try {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(v, v);
            }
            // Try YouTube volume if available (best-effort)
            try {
                if (youTubePlayer != null) {
                    int percent = (int)(v * 100f);
                    java.lang.reflect.Method m = youTubePlayer.getClass().getMethod("setVolume", int.class);
                    m.invoke(youTubePlayer, percent);
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            Log.w(TAG, "setVolume failed", t);
        }
    }

    private void onRestartClicked() {
        if (!restartArmed) {
            restartArmed = true;
            seekTo(0);
            if (progressHandler == null) progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            progressHandler.postDelayed(() -> restartArmed = false, 600);
        } else {
            restartArmed = false;
            prevTrack();
        }
    }

    private void seekTo(long ms) {
        Track t = getCurrentTrack();
        if (t == null) return;
        if (t.type == Track.Type.FILE && mediaPlayer != null) {
            try { mediaPlayer.seekTo((int) ms); } catch (Throwable ignore) {}
        } else if (t.type == Track.Type.YOUTUBE && youTubePlayer != null) {
            try { youTubePlayer.seekTo((float) (ms / 1000f)); } catch (Throwable ignore) {}
        }
    }

    private void play() {
        Track t = getCurrentTrack();
        if (t == null) return;
        if (t.type == Track.Type.FILE) {
            if (mediaPlayer != null) {
                if (mpPrepared) {
                    try { mediaPlayer.start(); startProgressUpdates(); } catch (Throwable ignore) {}
                } else {
                    // Not prepared yet; play when ready
                    mpPendingPlay = true;
                }
            } else {
                loadTrack(currentTrackIndex, true);
            }
        } else {
            if (youTubePlayer != null) {
                try { youTubePlayer.play(); } catch (Throwable ignore) {}
            } else {
                // Ensure the current YouTube track is loaded and auto-play once ready
                ytPendingPlay = true;
                loadTrack(currentTrackIndex, true);
            }
        }
    }

    private void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        } catch (Throwable ignore) {}
        try {
            if (youTubePlayer != null) youTubePlayer.pause();
        } catch (Throwable ignore) {}
        stopProgressUpdates();
    }

    private void startProgressUpdates() {
        if (progressHandler == null) progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressHandler != null) progressHandler.removeCallbacks(progressRunnable);
    }

    private void updateProgressUi() {
        Track t = getCurrentTrack();
        if (t == null || timeSlider == null || trackTimeView == null) return;
        if (t.type == Track.Type.FILE && mediaPlayer != null) {
            int cur = 0; int dur = 0;
            try { cur = mediaPlayer.getCurrentPosition(); } catch (Throwable ignore) {}
            try { dur = mediaPlayer.getDuration(); } catch (Throwable ignore) {}
            if (dur <= 0) dur = (int) Math.max(1, t.durationMs);
            timeSlider.setValueTo(Math.max(1, dur));
            timeSlider.setValue(cur);
            trackTimeView.setText(formatTime(cur) + " / " + formatTime(dur));
        }
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" + sec : String.valueOf(sec));
    }

    // Network connectivity check for YouTube loading
    private boolean isNetworkAvailable() {
        try {
            android.content.Context ctx = requireContext().getApplicationContext();
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.net.Network n = cm.getActiveNetwork();
                if (n == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private void prevTrack() {
        if (playlist.isEmpty()) return;
        currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        loadTrack(currentTrackIndex, true);
    }
    private void nextTrack() {
        if (playlist.isEmpty()) return;
        currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        loadTrack(currentTrackIndex, true);
    }

    private Track getCurrentTrack() {
        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) return null;
        return playlist.get(currentTrackIndex);
    }

    private void stopYouTubeIfPlaying() {
        try {
            if (youTubePlayer != null) {
                youTubePlayer.pause();
            }
        } catch (Throwable ignore) {}
        ytPendingPlay = false;
    }

    private void loadTrack(int index, boolean autoPlay) {
        if (index < 0 || index >= playlist.size()) return;
        currentTrackIndex = index;
        Track t = playlist.get(index);

        // Update title
        if (trackNameView != null) trackNameView.setText(t.title != null ? t.title : "");

        // Reset progress
        if (timeSlider != null) { timeSlider.setValueFrom(0f); timeSlider.setValueTo(1f); timeSlider.setValue(0f); }
        if (trackTimeView != null) trackTimeView.setText("0:00 / 0:00");

        // Always stop the other engine before starting a new track
        if (t.type == Track.Type.YOUTUBE) {
            // Stop local audio engine
            releaseMediaPlayer();
        } else {
            // Stop YouTube engine to ensure no overlap
            stopYouTubeIfPlaying();
        }

        if (t.type == Track.Type.YOUTUBE) {
            // Per requirement: keep YouTube view hidden and show artwork instead
            if (youTubePlayerView != null) youTubePlayerView.setVisibility(View.GONE);
            if (audioArtwork != null) audioArtwork.setVisibility(View.VISIBLE);

            // Check connectivity before attempting to load YouTube
            if (!isNetworkAvailable()) {
                try { android.widget.Toast.makeText(requireContext(), "No internet connection", android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
                ytPendingPlay = false;
                if (youtubeLoading != null) youtubeLoading.setVisibility(View.GONE);
                return;
            }

            // Show loading indicator while we cue/play
            if (youtubeLoading != null) youtubeLoading.setVisibility(View.VISIBLE);

            if (youTubePlayer != null) {
                String videoId = extractYouTubeId(t.uri);
                if (videoId != null) {
                    try {
                        youTubePlayer.cueVideo(videoId, 0f);
                        if (autoPlay || ytPendingPlay) {
                            ytPendingPlay = false;
                            youTubePlayer.play();
                        }
                    } catch (Throwable ignore) {}
                }
            } else if (autoPlay) {
                // Player not yet ready; remember to start on onReady
                ytPendingPlay = true;
            }
        } else {
            // FILE track
            if (youTubePlayerView != null) youTubePlayerView.setVisibility(View.GONE);
            if (audioArtwork != null) audioArtwork.setVisibility(View.VISIBLE);
            try {
                mpPrepared = false;
                mpPendingPlay = false;
                mediaPlayer = new MediaPlayer();
                String src = t.uri;
                try {
                    Uri u = Uri.parse(src);
                    String scheme = u != null ? u.getScheme() : null;
                    if (scheme != null && (scheme.equalsIgnoreCase("content") || scheme.equalsIgnoreCase("file"))) {
                        mediaPlayer.setDataSource(requireContext(), u);
                    } else {
                        File f = new File(src);
                        if (f.exists()) {
                            mediaPlayer.setDataSource(src);
                        } else if (scheme == null && src != null && src.startsWith("/")) {
                            mediaPlayer.setDataSource(src);
                        } else {
                            // Fallback: try as context URI
                            mediaPlayer.setDataSource(requireContext(), u);
                        }
                    }
                } catch (Throwable setDsErr) {
                    Log.w(TAG, "Primary setDataSource failed, trying file fallback: " + src, setDsErr);
                    try {
                        File f2 = new File(src);
                        if (f2.exists()) {
                            mediaPlayer.setDataSource(src);
                        }
                    } catch (Throwable ignore2) {}
                }
                mediaPlayer.setOnPreparedListener(mp -> {
                    try {
                        mpPrepared = true;
                        int dur = mp.getDuration();
                        if (timeSlider != null) { timeSlider.setValueFrom(0f); timeSlider.setValueTo(Math.max(1, dur)); timeSlider.setValue(0f); }
                        if (trackTimeView != null) trackTimeView.setText("0:00 / " + formatTime(dur));
                        if (autoPlay || mpPendingPlay) {
                            mpPendingPlay = false;
                            mp.start();
                            startProgressUpdates();
                        }
                    } catch (Throwable ignore) {}
                });
                mediaPlayer.setOnCompletionListener(mp -> stopProgressUpdates());
                mediaPlayer.prepareAsync();
            } catch (Throwable e) {
                Log.e(TAG, "Failed to load audio: " + t.uri, e);
            }
        }
    }

    private void releaseMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                stopProgressUpdates();
                mediaPlayer.reset();
                mediaPlayer.release();
            }
        } catch (Throwable ignore) {}
        mediaPlayer = null;
    }

    private boolean isYouTubeUrl(String s) {
        if (s == null) return false;
        String u = s.toLowerCase();
        return u.contains("youtube.com/watch") || u.contains("youtu.be/");
    }
    private String extractYouTubeId(String url) {
        if (url == null) return null;
        try {
            if (url.contains("youtu.be/")) {
                String part = url.substring(url.indexOf("youtu.be/") + 9);
                int q = part.indexOf('?');
                return q >= 0 ? part.substring(0, q) : part;
            }
            int v = url.indexOf("v=");
            if (v >= 0) {
                String part = url.substring(v + 2);
                int amp = part.indexOf('&');
                return amp >= 0 ? part.substring(0, amp) : part;
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private void buildPlaylistFromAudioEntities(List<AudioEntity> list) {
        playlist.clear();
        if (list == null) return;
        for (AudioEntity a : list) {
            if (a == null) continue;
            String uri = a.getUri();
            String filePath = null;
            try { filePath = a.getFilePath(); } catch (Throwable ignore) {}
            // Prefer local copied file_path when available (more reliable than transient content URIs)
            String source = null;
            if (filePath != null && !filePath.trim().isEmpty()) {
                File f = new File(filePath);
                if (f.exists()) source = filePath;
            }
            if (source == null) {
                // Fallback to original uri
                if (uri != null && !uri.trim().isEmpty()) source = uri;
            }
            if (source == null) continue;

            String name = a.getName();
            if (name == null || name.trim().isEmpty()) {
                // Derive a friendly display name from the source
                try {
                    Uri u = Uri.parse(source);
                    String scheme = u != null ? u.getScheme() : null;
                    if (scheme != null && (scheme.equalsIgnoreCase("content") || scheme.equalsIgnoreCase("file"))) {
                        String last = u.getLastPathSegment();
                        if (last != null && !last.trim().isEmpty()) name = last;
                    }
                } catch (Throwable ignore) {}
                if (name == null || name.trim().isEmpty()) {
                    try {
                        java.io.File f = new java.io.File(source);
                        String fn = f.getName();
                        if (fn != null && !fn.trim().isEmpty()) name = fn;
                    } catch (Throwable ignore) {}
                }
                if (name == null || name.trim().isEmpty()) name = source;
            }
            // Classify as YouTube vs local file
            if (isYouTubeUrl(source)) {
                playlist.add(new Track(Track.Type.YOUTUBE, name, source, 0));
            } else {
                long dur = 0;
                try { dur = a.getDuration(); } catch (Throwable ignore) {}
                playlist.add(new Track(Track.Type.FILE, name, source, dur));
            }
        }
        currentTrackIndex = 0;
    }

    private void showBottomDialog() {
        if (bottomPlayerContainer == null) return;
        bottomPlayerContainer.setVisibility(View.VISIBLE);
    }

    private void hideBottomDialog() {
        if (bottomPlayerContainer == null) return;
        bottomPlayerContainer.setVisibility(View.GONE);
    }

    private boolean isBottomDialogVisible() {
        return bottomPlayerContainer != null && bottomPlayerContainer.getVisibility() == View.VISIBLE;
    }

    private void stopPlayerIfPlaying() {
        try {
            if (youTubePlayer != null) {
                youTubePlayer.pause();
            }
        } catch (Throwable t) {
            Log.w(TAG, "stopPlayerIfPlaying: failed to pause YT", t);
        }
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Throwable t) {
            Log.w(TAG, "stopPlayerIfPlaying: failed to pause MP", t);
        }
    }

    private void setupPageNavigation() {
        if (photoView == null) return;
        // Keep OnViewTapListener as a fallback (fires when PhotoView recognizes a tap on the view)

        // Double-tap in the center area enters annotation mode
        photoView.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                int total = getTotalPageCount();
                if (total <= 0) return false;

                int width = photoView.getWidth();
                if (width <= 0) return false;

                float centerStart = width / 3f;
                float centerEnd = width * 2f / 3f;

                if (e.getX() < centerStart) {
                    if (currentPage > 0) {
                        Log.d(TAG, "Nav(FB): view tap left side -> prev page");
                        setCurrentPage(currentPage - 1);
                    }
                } else if (e.getX() > centerEnd) {
                    if (currentPage < total - 1) {
                        Log.d(TAG, "Nav(FB): view tap right side -> next page");
                        setCurrentPage(currentPage + 1);
                    }
                } else {
                    Log.d(TAG, "Nav(FB): view tap center -> toggle UI");
                    toggleUiChrome();
                }

                return false;
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
                float left = w / 3f;
                float right = w * 2f / 3f;
                float top = h / 3f;
                float bottom = h * 2f / 3f;
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    Log.d(TAG, "Nav: double-tap center -> enter annotation mode (hide bottom player and pause)");
                    hideBottomDialog();
                    stopPlayerIfPlaying();
                    enterAnnotationMode();
                    return true;
                }
                return false;
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
        // Also clear static reference if pointing to this instance
        try {
            if (sLastInstanceRef != null && sLastInstanceRef.get() == this) {
                sLastInstanceRef.clear();
            }
        } catch (Throwable ignore) {}
        // Release YouTube player if present to avoid leaks and background work
        try {
            if (youTubePlayerView != null) {
                youTubePlayerView.release();
            }
        } catch (Throwable t) {
            Log.w(TAG, "onDestroyView: failed to release YouTubePlayerView", t);
        } finally {
            youTubePlayerView = null;
            youTubePlayer = null;
            bottomPlayerContainer = null; // will be re-inflated if needed next time
        }
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

        android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_pen_preset, null, false);

        com.example.staffpad.views.PenPreviewView preview = dialogView.findViewById(R.id.penPreview);
        android.widget.EditText nameEt = dialogView.findViewById(R.id.presetName);
        com.google.android.material.slider.Slider widthSeek = dialogView.findViewById(R.id.widthSeek);
        com.google.android.material.slider.Slider alphaSeek = dialogView.findViewById(R.id.alphaSeek);
        com.skydoves.colorpickerview.ColorPickerView colorPicker = dialogView.findViewById(R.id.colorPickerView);

        // Initialize views from preset
        nameEt.setText(preset.name);
        preview.setStrokeColor(preset.color);
        preview.setStrokeWidthPx(Math.max(1f, preset.widthPx));
        preview.setStrokeAlpha(preset.alpha);

        // Configure width slider
        widthSeek.setValueFrom(1f);
        widthSeek.setValueTo(80f);
        widthSeek.setStepSize(1f);
        widthSeek.setValue(Math.max(1f, preset.widthPx));
        widthSeek.addOnChangeListener((slider, value, fromUser) -> {
            preview.setStrokeWidthPx(Math.max(1f, value));
        });

        // Configure opacity slider (0-100%)
        alphaSeek.setValueFrom(0f);
        alphaSeek.setValueTo(100f);
        alphaSeek.setStepSize(1f);
        int initialAlphaPct = (int) Math.round((preset.alpha / 255.0) * 100.0);
        alphaSeek.setValue(initialAlphaPct);
        alphaSeek.addOnChangeListener((slider, value, fromUser) -> {
            int a = (int) Math.round(value * 2.55);
            preview.setStrokeAlpha(a);
        });

        // Color picker
        final int[] pickedColor = new int[]{ preset.color };
        try {
            colorPicker.setInitialColor(preset.color);
        } catch (Throwable ignored) {}
        colorPicker.setColorListener((com.skydoves.colorpickerview.listeners.ColorEnvelopeListener) (envelope, fromUser) -> {
            pickedColor[0] = envelope.getColor();
            preview.setStrokeColor(pickedColor[0]);
        });

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Preset")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    preset.name = nameEt.getText().toString();
                    preset.widthPx = Math.max(1f, widthSeek.getValue());
                    preset.color = pickedColor[0];
                    preset.alpha = (int) Math.round(alphaSeek.getValue() * 2.55);
                    savePenPresetsToPrefs();
                    if (penPresetAdapter != null) penPresetAdapter.notifyItemChanged(index);
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

            // Map overlay (view space) into the underlying image (drawable) space using PhotoView's display matrix.
            // Fallback to simple scaling if required information is missing.
            Bitmap finalBmp = null;
            int targetW = overlayBmp.getWidth();
            int targetH = overlayBmp.getHeight();
            try {
                android.graphics.drawable.Drawable d = photoView.getDrawable();
                android.graphics.RectF displayRect = photoView.getDisplayRect();
                if (d != null && d.getIntrinsicWidth() > 0 && d.getIntrinsicHeight() > 0 && displayRect != null && displayRect.width() > 0 && displayRect.height() > 0) {
                    targetW = d.getIntrinsicWidth();
                    targetH = d.getIntrinsicHeight();

                    // drawable -> view transform is: scale by sx,sy then translate by tx,ty
                    float sx = displayRect.width() / (float) targetW;
                    float sy = displayRect.height() / (float) targetH;
                    float tx = displayRect.left;
                    float ty = displayRect.top;

                    // We need inverse to map view-space overlay into drawable-space
                    android.graphics.Matrix inv = new android.graphics.Matrix();
                    inv.postTranslate(-tx, -ty);
                    inv.postScale(1f / Math.max(1e-6f, sx), 1f / Math.max(1e-6f, sy));

                    finalBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas mapCanvas = new android.graphics.Canvas(finalBmp);
                    mapCanvas.drawBitmap(overlayBmp, inv, null);
                    // We no longer need the overlay bitmap
                    overlayBmp.recycle();
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "Falling back to naive scaling during save due to mapping error", t);
            }

            if (finalBmp == null) {
                // Fallback: scale overlay bitmap to drawable's intrinsic size if available, else keep as-is
                try {
                    android.graphics.drawable.Drawable d = photoView.getDrawable();
                    if (d != null && d.getIntrinsicWidth() > 0 && d.getIntrinsicHeight() > 0) {
                        targetW = d.getIntrinsicWidth();
                        targetH = d.getIntrinsicHeight();
                    }
                } catch (Throwable ignored) {}
                if (overlayBmp.getWidth() != targetW || overlayBmp.getHeight() != targetH) {
                    finalBmp = Bitmap.createScaledBitmap(overlayBmp, targetW, targetH, true);
                    if (finalBmp != overlayBmp) overlayBmp.recycle();
                } else {
                    finalBmp = overlayBmp;
                }
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
            // Make bitmap effectively final for use inside the background thread
            final Bitmap finalBmpToSave = finalBmp;
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
                        finalBmpToSave.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush(); fos.close();
                    } finally {
                        finalBmpToSave.recycle();
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
