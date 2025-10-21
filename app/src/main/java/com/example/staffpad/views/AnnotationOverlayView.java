package com.example.staffpad.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Simple transparent overlay view that supports freehand drawing, text boxes, eraser, and undo/redo.
 * This is a minimal implementation to satisfy initial requirements.
 */
public class AnnotationOverlayView extends View {

    public enum ToolMode { NONE, PEN, ERASER, TEXT }

    // Stylus/Jetpack Ink integration flags (no hard dependency; graceful fallback)
    private boolean inkEnabled = true; // default on for better pens
    private boolean stylusInUse = false;
    private boolean tempEraserFromButton = false;

    public static abstract class AnnotationItem {
        public abstract void draw(Canvas c);
        public abstract boolean hitTest(float x, float y);
    }

    public static class Stroke extends AnnotationItem {
        public final Path path = new Path();
        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Sampled points for serialization/history
        public final java.util.List<float[]> points = new java.util.ArrayList<>();
        public Stroke(int color, float width, int alpha) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color);
            paint.setStrokeWidth(width);
            paint.setAlpha(alpha);
        }
        public void addPoint(float x, float y, boolean moveTo) {
            if (moveTo) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            points.add(new float[]{x, y});
        }
        @Override public void draw(Canvas c) { c.drawPath(path, paint); }
        @Override public boolean hitTest(float x, float y) {
            // Basic hit test: not implemented for vector hit; return false
            return false;
        }
    }

    public static class EraseStroke extends AnnotationItem {
        public final Path path = new Path();
        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Sampled points for serialization/history
        public final java.util.List<float[]> points = new java.util.ArrayList<>();
        public EraseStroke(float widthPx) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(widthPx);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        public void addPoint(float x, float y, boolean moveTo) {
            if (moveTo) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            points.add(new float[]{x, y});
        }
        @Override public void draw(Canvas c) { c.drawPath(path, paint); }
        @Override public boolean hitTest(float x, float y) { return false; }
    }

    public static class TextBox extends AnnotationItem {
        public String text = "";
        public float x, y;
        public final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        public boolean bold = false;
        public float textSizeSp = 16f;
        public TextBox(float x, float y, int color, float textSizeSp, boolean bold) {
            this.x = x; this.y = y; this.bold = bold; this.textSizeSp = textSizeSp;
            paint.setColor(color);
            paint.setTextSize(spToPx(textSizeSp));
            paint.setFakeBoldText(bold);
        }
        @Override public void draw(Canvas c) {
            if (text != null) {
                c.drawText(text, x, y, paint);
            }
        }
        @Override public boolean hitTest(float hx, float hy) {
            if (text == null) return false;
            float w = paint.measureText(text);
            float h = paint.getTextSize();
            return hx >= x && hx <= x + w && hy >= (y - h) && hy <= y;
        }
    }

    private final List<AnnotationItem> items = new ArrayList<>();
    private final Deque<AnnotationItem> undoStack = new ArrayDeque<>();
    private final Deque<AnnotationItem> redoStack = new ArrayDeque<>();

    private ToolMode mode = ToolMode.NONE;
    private int penColor = Color.BLACK;
    private int penAlpha = 255;
    private float penWidthPx = 6f;
    private float eraserWidthPx = 20f;

    private Stroke currentStroke;
    private EraseStroke currentErase;
    private TextBox currentTextBox;

    // Text selection/drag/edit helpers
    private TextBox selectedTextBox;
    private TextBox draggingTextBox;
    private float dragOffsetX;
    private float dragOffsetY;
    private float lastDownX;
    private float lastDownY;
    private boolean didMove;

    // Paint for selection rectangle
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AnnotationOverlayView(Context context) {
        super(context);
        init();
    }
    public AnnotationOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public AnnotationOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        // Use software layer so CLEAR xfermode works reliably
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(3f);
        selectionPaint.setColor(Color.BLUE);
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
        if (mode != ToolMode.TEXT) {
            selectedTextBox = null;
            draggingTextBox = null;
        }
        invalidate();
    }
    public ToolMode getMode() { return mode; }

    public void setPen(int color, float widthPx, int alpha) {
        this.penColor = color; this.penWidthPx = widthPx; this.penAlpha = alpha;
    }
    public void setEraserWidth(float widthPx) { this.eraserWidthPx = Math.max(1f, widthPx); }
    public float getEraserWidth() { return this.eraserWidthPx; }

    public void clear() {
        items.clear();
        undoStack.clear();
        redoStack.clear();
        invalidate();
    }

    public void undo() {
        if (!items.isEmpty()) {
            AnnotationItem item = items.remove(items.size()-1);
            undoStack.push(item);
            invalidate();
        }
    }

    public void redo() {
        if (!undoStack.isEmpty()) {
            AnnotationItem item = undoStack.pop();
            items.add(item);
            invalidate();
        }
    }

    public List<AnnotationItem> getItemsSnapshot() {
        return new ArrayList<>(items);
    }

    public void addPenPreset(int color, float widthPx, int alpha) {
        setPen(color, widthPx, alpha);
        setMode(ToolMode.PEN);
    }

    public Bitmap exportToBitmap(int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // Draw only persistent annotation content (strokes, text). Do not include transient UI like selection rectangles.
        for (AnnotationItem it : items) {
            it.draw(c);
        }
        if (currentStroke != null) currentStroke.draw(c);
        if (currentErase != null) currentErase.draw(c);
        if (currentTextBox != null) currentTextBox.draw(c);
        return bmp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (AnnotationItem it : items) {
            it.draw(canvas);
        }
        if (currentStroke != null) currentStroke.draw(canvas);
        if (currentErase != null) currentErase.draw(canvas);
        if (currentTextBox != null) currentTextBox.draw(canvas);

        // Draw selection rectangle around selected text when in TEXT mode
        if (mode == ToolMode.TEXT && selectedTextBox != null && selectedTextBox.text != null) {
            float w = selectedTextBox.paint.measureText(selectedTextBox.text);
            float h = selectedTextBox.paint.getTextSize();
            float left = selectedTextBox.x;
            float top = selectedTextBox.y - h;
            float right = selectedTextBox.x + w;
            float bottom = selectedTextBox.y;
            // Slight padding
            float pad = 6f;
            canvas.drawRect(left - pad, top - pad, right + pad, bottom + pad, selectionPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Prefer stylus pointer when present
        int stylusIndex = findStylusPointerIndex(event);
        boolean hasStylus = stylusIndex >= 0;
        if (hasStylus) {
            stylusInUse = event.getActionMasked() != MotionEvent.ACTION_UP && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
        } else {
            // If a stylus is currently in proximity (tracked by previous events), ignore finger input for palm rejection
            if (stylusInUse && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                return false;
            }
        }
        float x = event.getX(hasStylus ? stylusIndex : 0);
        float y = event.getY(hasStylus ? stylusIndex : 0);

        // Stylus button handling: primary button acts as temporary eraser
        if (hasStylus) {
            int buttons = event.getButtonState();
            boolean primaryPressed = (buttons & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
            if (primaryPressed && !tempEraserFromButton && mode == ToolMode.PEN) {
                tempEraserFromButton = true;
            } else if (!primaryPressed && tempEraserFromButton) {
                // Release temporary eraser when button is released
                tempEraserFromButton = false;
            }
        }

        ToolMode effectiveMode = (tempEraserFromButton ? ToolMode.ERASER : mode);
        switch (effectiveMode) {
            case PEN:
                handlePenTouch(event, x, y);
                return true;
            case ERASER:
                handleEraserTouch(event, x, y);
                return true;
            case TEXT:
                return handleTextTouch(event, x, y);
            default:
                return false;
        }
    }

    private void handlePenTouch(MotionEvent event, float x, float y) {
        int pointerIndex = findStylusPointerIndex(event);
        if (pointerIndex < 0) pointerIndex = 0;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            currentStroke = new Stroke(penColor, penWidthPx, penAlpha);
            // Adjust width subtly by pressure when stylus-enabled
            if (inkEnabled) {
                try {
                    float pressure = event.getPressure(pointerIndex);
                    pressure = Math.max(0.1f, Math.min(pressure, 1.5f));
                    currentStroke.paint.setStrokeWidth(Math.max(1f, penWidthPx * (0.6f + 0.4f * pressure)));
                } catch (Throwable ignore) {}
            }
            currentStroke.addPoint(x, y, true);
            redoStack.clear();
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (currentStroke != null) {
                // use historical points for smoothing
                if (inkEnabled) addHistoricalPointsToStroke(currentStroke, event, pointerIndex);
                currentStroke.addPoint(x, y, false);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (currentStroke != null) {
                if (inkEnabled) addHistoricalPointsToStroke(currentStroke, event, pointerIndex);
                currentStroke.addPoint(x, y, false);
                items.add(currentStroke);
                currentStroke = null;
            }
            // Stylus ended; allow fingers again
            if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS) {
                stylusInUse = false;
                tempEraserFromButton = false;
            }
        }
        invalidate();
    }

    private void handleEraserTouch(MotionEvent event, float x, float y) {
        int pointerIndex = findStylusPointerIndex(event);
        if (pointerIndex < 0) pointerIndex = 0;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            currentErase = new EraseStroke(eraserWidthPx);
            currentErase.addPoint(x, y, true);
            redoStack.clear();
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (currentErase != null) {
                if (inkEnabled) addHistoricalPointsToErase(currentErase, event, pointerIndex);
                currentErase.addPoint(x, y, false);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (currentErase != null) {
                if (inkEnabled) addHistoricalPointsToErase(currentErase, event, pointerIndex);
                currentErase.addPoint(x, y, false);
                items.add(currentErase);
                currentErase = null;
            }
            if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS) {
                stylusInUse = false;
                tempEraserFromButton = false;
            }
        }
        invalidate();
    }

    private void startTextBox(float x, float y) {
        currentTextBox = new TextBox(x, y, Color.BLACK, 16f, false);
        selectedTextBox = currentTextBox;
        // For minimal version, show a simple editable text via callback; consumer can set text later
        if (onRequestEditTextListener != null) {
            onRequestEditTextListener.onRequestEdit(currentTextBox);
        }
        // Add immediately (text may be empty until edited)
        items.add(currentTextBox);
        currentTextBox = null;
        invalidate();
    }

    private boolean handleTextTouch(MotionEvent event, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                draggingTextBox = findTopmostTextBoxAt(x, y);
                selectedTextBox = draggingTextBox; // highlight the one we're about to move/edit
                lastDownX = x;
                lastDownY = y;
                didMove = false;
                if (draggingTextBox != null) {
                    dragOffsetX = x - draggingTextBox.x;
                    dragOffsetY = y - draggingTextBox.y;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingTextBox != null) {
                    float newX = x - dragOffsetX;
                    float newY = y - dragOffsetY;
                    draggingTextBox.x = newX;
                    draggingTextBox.y = newY;
                    didMove = true;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (draggingTextBox != null) {
                    if (!didMove && onRequestEditTextListener != null) {
                        onRequestEditTextListener.onRequestEdit(draggingTextBox);
                    }
                    draggingTextBox = null;
                    didMove = false;
                    invalidate();
                } else {
                    // No existing box tapped; create a new one at this location
                    startTextBox(x, y);
                }
                return true;
            default:
                return false;
        }
    }

    private TextBox findTopmostTextBoxAt(float x, float y) {
        for (int i = items.size() - 1; i >= 0; i--) {
            AnnotationItem it = items.get(i);
            if (it instanceof TextBox) {
                TextBox tb = (TextBox) it;
                if (tb.hitTest(x, y)) return tb;
            }
        }
        return null;
    }

    public interface OnRequestEditTextListener {
        void onRequestEdit(TextBox textBox);
    }
    private OnRequestEditTextListener onRequestEditTextListener;
    public void setOnRequestEditTextListener(OnRequestEditTextListener l) { this.onRequestEditTextListener = l; }

    public void setItemsFromHistory(List<AnnotationItem> history) {
        items.clear();
        undoStack.clear();
        redoStack.clear();
        if (history != null) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                AnnotationItem it = history.get(i);
                // Defensive: only accept known types
                if (it instanceof Stroke || it instanceof EraseStroke || it instanceof TextBox) {
                    items.add(it);
                }
            }
        }
        invalidate();
    }

    // --- Stylus helpers and Jetpack Ink (reflection-ready) ---
    private int findStylusPointerIndex(MotionEvent e) {
        int pc = e.getPointerCount();
        for (int i = 0; i < pc; i++) {
            if (e.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) return i;
        }
        return -1;
    }

    public void setInkEnabled(boolean enabled) { this.inkEnabled = enabled; }

    private void addHistoricalPointsToStroke(Stroke s, MotionEvent e, int pointerIndex) {
        if (s == null) return;
        final int hs = e.getHistorySize();
        for (int i = 0; i < hs; i++) {
            float hx = e.getHistoricalX(pointerIndex, i);
            float hy = e.getHistoricalY(pointerIndex, i);
            s.addPoint(hx, hy, false);
        }
    }

    private void addHistoricalPointsToErase(EraseStroke s, MotionEvent e, int pointerIndex) {
        if (s == null) return;
        final int hs = e.getHistorySize();
        for (int i = 0; i < hs; i++) {
            float hx = e.getHistoricalX(pointerIndex, i);
            float hy = e.getHistoricalY(pointerIndex, i);
            s.addPoint(hx, hy, false);
        }
    }

    private static float spToPx(float sp) {
        return sp * (Resources.getSystem().getDisplayMetrics().scaledDensity);
    }
}
