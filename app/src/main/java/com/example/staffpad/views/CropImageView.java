package com.example.staffpad.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class CropImageView extends View {

    private Bitmap originalBitmap;
    private Matrix imageMatrix = new Matrix();
    private Paint imagePaint = new Paint();

    // Crop rectangle
    private RectF cropRect = new RectF();
    private Paint cropBorderPaint = new Paint();
    private Paint cropHandlePaint = new Paint();
    private Paint overlayPaint = new Paint();
    private Paint gridPaint = new Paint();

    // Rotation
    private float rotationAngle = 0f;

    // Touch handling
    private enum DragMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT
    }
    private DragMode currentDragMode = DragMode.NONE;
    private PointF lastTouchPoint = new PointF();

    // Zoom handling
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float scaleFactor = 1.0f;
    private float minScale = 0.5f;
    private float maxScale = 5.0f;
    private PointF panOffset = new PointF(0, 0);

    // Handle size
    private static final float HANDLE_SIZE = 60f;
    private static final float EDGE_TOUCH_THRESHOLD = 40f;

    // Callback
    public interface CropChangeListener {
        void onCropChanged(RectF cropRect, float rotation);
    }
    private CropChangeListener cropChangeListener;

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Setup paint for crop rectangle border
        cropBorderPaint.setColor(Color.WHITE);
        cropBorderPaint.setStyle(Paint.Style.STROKE);
        cropBorderPaint.setStrokeWidth(4f);
        cropBorderPaint.setAntiAlias(true);

        // Setup paint for crop handles
        cropHandlePaint.setColor(Color.WHITE);
        cropHandlePaint.setStyle(Paint.Style.FILL);
        cropHandlePaint.setAntiAlias(true);

        // Setup paint for overlay (darkened area outside crop)
        overlayPaint.setColor(Color.argb(150, 0, 0, 0));
        overlayPaint.setStyle(Paint.Style.FILL);

        // Setup paint for grid lines
        gridPaint.setColor(Color.argb(100, 255, 255, 255));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        // Image paint
        imagePaint.setAntiAlias(true);
        imagePaint.setFilterBitmap(true);

        // Initialize zoom and pan
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.originalBitmap = bitmap;
        resetCropRect();
        invalidate();
    }

    private void resetCropRect() {
        if (originalBitmap == null) return;

        // Center the image
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float bitmapWidth = originalBitmap.getWidth();
        float bitmapHeight = originalBitmap.getHeight();

        // Calculate scale to fit
        float scale = Math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight) * 0.8f;

        // Calculate position to center
        float left = (viewWidth - bitmapWidth * scale) / 2;
        float top = (viewHeight - bitmapHeight * scale) / 2;

        cropRect.set(left, top, left + bitmapWidth * scale, top + bitmapHeight * scale);
    }

    public void setRotation(float angle) {
        this.rotationAngle = angle;
        invalidate();
    }

    public float getRotation() {
        return rotationAngle;
    }

    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    public void setCropChangeListener(CropChangeListener listener) {
        this.cropChangeListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (originalBitmap == null) return;

        // Save canvas state
        canvas.save();

        // Apply pan and zoom
        canvas.translate(panOffset.x, panOffset.y);
        canvas.scale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f);

        // Apply rotation around center of crop rect
        canvas.rotate(rotationAngle, cropRect.centerX(), cropRect.centerY());

        // Draw the bitmap
        canvas.drawBitmap(originalBitmap, null, cropRect, imagePaint);

        // Restore rotation for overlays
        canvas.rotate(-rotationAngle, cropRect.centerX(), cropRect.centerY());

        // Draw darkened overlay outside crop area
        drawOverlay(canvas);

        // Draw crop rectangle border
        canvas.drawRect(cropRect, cropBorderPaint);

        // Draw grid lines (rule of thirds)
        drawGrid(canvas);

        // Draw corner handles
        drawHandles(canvas);

        // Restore canvas
        canvas.restore();
    }

    private void drawOverlay(Canvas canvas) {
        // Draw semi-transparent overlay outside crop rect
        // Top
        canvas.drawRect(0, 0, getWidth(), cropRect.top, overlayPaint);
        // Bottom
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
        // Left
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        // Right
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);
    }

    private void drawGrid(Canvas canvas) {
        // Draw rule of thirds grid
        float thirdWidth = cropRect.width() / 3;
        float thirdHeight = cropRect.height() / 3;

        // Vertical lines
        canvas.drawLine(cropRect.left + thirdWidth, cropRect.top,
                cropRect.left + thirdWidth, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + 2 * thirdWidth, cropRect.top,
                cropRect.left + 2 * thirdWidth, cropRect.bottom, gridPaint);

        // Horizontal lines
        canvas.drawLine(cropRect.left, cropRect.top + thirdHeight,
                cropRect.right, cropRect.top + thirdHeight, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + 2 * thirdHeight,
                cropRect.right, cropRect.top + 2 * thirdHeight, gridPaint);
    }

    private void drawHandles(Canvas canvas) {
        float handleRadius = HANDLE_SIZE / 2;

        // Corner handles
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, cropHandlePaint);

        // Edge handles (midpoints)
        canvas.drawCircle(cropRect.centerX(), cropRect.top, handleRadius * 0.7f, cropHandlePaint);
        canvas.drawCircle(cropRect.centerX(), cropRect.bottom, handleRadius * 0.7f, cropHandlePaint);
        canvas.drawCircle(cropRect.left, cropRect.centerY(), handleRadius * 0.7f, cropHandlePaint);
        canvas.drawCircle(cropRect.right, cropRect.centerY(), handleRadius * 0.7f, cropHandlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = scaleDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;

        // Handle crop rectangle dragging
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouchPoint.set(event.getX(), event.getY());
                currentDragMode = getDragMode(event.getX(), event.getY());
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentDragMode != DragMode.NONE) {
                    float dx = event.getX() - lastTouchPoint.x;
                    float dy = event.getY() - lastTouchPoint.y;
                    updateCropRect(dx, dy);
                    lastTouchPoint.set(event.getX(), event.getY());
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentDragMode = DragMode.NONE;
                if (cropChangeListener != null) {
                    cropChangeListener.onCropChanged(cropRect, rotationAngle);
                }
                break;
        }

        return handled || currentDragMode != DragMode.NONE || super.onTouchEvent(event);
    }

    private DragMode getDragMode(float x, float y) {
        float handleRadius = HANDLE_SIZE;

        // Check corners first
        if (isNearPoint(x, y, cropRect.left, cropRect.top, handleRadius)) {
            return DragMode.TOP_LEFT;
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.top, handleRadius)) {
            return DragMode.TOP_RIGHT;
        }
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom, handleRadius)) {
            return DragMode.BOTTOM_LEFT;
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom, handleRadius)) {
            return DragMode.BOTTOM_RIGHT;
        }

        // Check edges
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.top, handleRadius)) {
            return DragMode.TOP;
        }
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.bottom, handleRadius)) {
            return DragMode.BOTTOM;
        }
        if (isNearPoint(x, y, cropRect.left, cropRect.centerY(), handleRadius)) {
            return DragMode.LEFT;
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.centerY(), handleRadius)) {
            return DragMode.RIGHT;
        }

        // Check if inside crop rect for moving
        if (cropRect.contains(x, y)) {
            return DragMode.MOVE;
        }

        return DragMode.NONE;
    }

    private boolean isNearPoint(float x, float y, float px, float py, float threshold) {
        float dx = x - px;
        float dy = y - py;
        return (dx * dx + dy * dy) <= (threshold * threshold);
    }

    private void updateCropRect(float dx, float dy) {
        switch (currentDragMode) {
            case MOVE:
                cropRect.offset(dx, dy);
                break;
            case TOP_LEFT:
                cropRect.left += dx;
                cropRect.top += dy;
                break;
            case TOP_RIGHT:
                cropRect.right += dx;
                cropRect.top += dy;
                break;
            case BOTTOM_LEFT:
                cropRect.left += dx;
                cropRect.bottom += dy;
                break;
            case BOTTOM_RIGHT:
                cropRect.right += dx;
                cropRect.bottom += dy;
                break;
            case TOP:
                cropRect.top += dy;
                break;
            case BOTTOM:
                cropRect.bottom += dy;
                break;
            case LEFT:
                cropRect.left += dx;
                break;
            case RIGHT:
                cropRect.right += dx;
                break;
        }

        // Ensure minimum size
        if (cropRect.width() < 100) {
            if (currentDragMode == DragMode.LEFT || currentDragMode == DragMode.TOP_LEFT ||
                    currentDragMode == DragMode.BOTTOM_LEFT) {
                cropRect.left = cropRect.right - 100;
            } else {
                cropRect.right = cropRect.left + 100;
            }
        }
        if (cropRect.height() < 100) {
            if (currentDragMode == DragMode.TOP || currentDragMode == DragMode.TOP_LEFT ||
                    currentDragMode == DragMode.TOP_RIGHT) {
                cropRect.top = cropRect.bottom - 100;
            } else {
                cropRect.bottom = cropRect.top + 100;
            }
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(minScale, Math.min(scaleFactor, maxScale));
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (currentDragMode == DragMode.NONE) {
                panOffset.x -= distanceX;
                panOffset.y -= distanceY;
                invalidate();
                return true;
            }
            return false;
        }
    }

    public void reset() {
        rotationAngle = 0;
        scaleFactor = 1.0f;
        panOffset.set(0, 0);
        resetCropRect();
        invalidate();
    }
}