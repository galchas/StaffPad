package com.example.staffpad.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CropImageView extends View {
    private static final String TAG = "CropImageView";
    private static final int CORNER_TOUCH_RADIUS = 50;
    private static final int EDGE_TOUCH_RADIUS = 30;

    private Bitmap bitmap;
    private RectF imageBounds = new RectF();
    private RectF cropRect = new RectF();
    private Paint cropPaint;
    private Paint overlayPaint;
    private Paint cornerPaint;

    private enum DragMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT_EDGE, RIGHT_EDGE, TOP_EDGE, BOTTOM_EDGE
    }

    private DragMode dragMode = DragMode.NONE;
    private float lastTouchX;
    private float lastTouchY;
    private float rotation = 0f;

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cropPaint = new Paint();
        cropPaint.setColor(Color.WHITE);
        cropPaint.setStyle(Paint.Style.STROKE);
        cropPaint.setStrokeWidth(3f);

        overlayPaint = new Paint();
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(128);

        cornerPaint = new Paint();
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.FILL);
        cornerPaint.setStrokeWidth(8f);
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        if (bitmap != null) {
            calculateImageBounds();
            resetCrop();
        }
        invalidate();
    }

    public void setRotation(float degrees) {
        this.rotation = degrees;
        invalidate();
    }

    public void resetCrop() {
        if (bitmap != null) {
            // Set crop rect to full image with small padding for handles
            float padding = 6;
            cropRect.set(
                    imageBounds.left + padding,
                    imageBounds.top + padding,
                    imageBounds.right - padding,
                    imageBounds.bottom - padding
            );
            invalidate();
        }
    }

    private void calculateImageBounds() {
        if (bitmap == null) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float imageWidth = bitmap.getWidth();
        float imageHeight = bitmap.getHeight();

        // Calculate scale to fit image in view with minimal margins for larger preview
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight) * 0.98f;

        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        // Center the image
        float left = (viewWidth - scaledWidth) / 2;
        float top = (viewHeight - scaledHeight) / 2;

        imageBounds.set(left, top, left + scaledWidth, top + scaledHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null) {
            calculateImageBounds();
            resetCrop();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null) return;

        // Draw the image
        canvas.save();
        canvas.rotate(rotation, getWidth() / 2f, getHeight() / 2f);
        canvas.drawBitmap(bitmap, null, imageBounds, null);
        canvas.restore();

        // Draw overlay (darkened area outside crop)
        canvas.drawRect(0, 0, getWidth(), cropRect.top, overlayPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), overlayPaint);

        // Draw crop rectangle
        canvas.drawRect(cropRect, cropPaint);

        // Draw corners
        drawCorner(canvas, cropRect.left, cropRect.top);
        drawCorner(canvas, cropRect.right, cropRect.top);
        drawCorner(canvas, cropRect.left, cropRect.bottom);
        drawCorner(canvas, cropRect.right, cropRect.bottom);

        // Draw edges
        drawEdge(canvas, cropRect.centerX(), cropRect.top);
        drawEdge(canvas, cropRect.centerX(), cropRect.bottom);
        drawEdge(canvas, cropRect.left, cropRect.centerY());
        drawEdge(canvas, cropRect.right, cropRect.centerY());

        // Draw grid lines
        Paint gridPaint = new Paint(cropPaint);
        gridPaint.setAlpha(100);
        float third = cropRect.width() / 3;
        canvas.drawLine(cropRect.left + third, cropRect.top,
                cropRect.left + third, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + 2 * third, cropRect.top,
                cropRect.left + 2 * third, cropRect.bottom, gridPaint);

        third = cropRect.height() / 3;
        canvas.drawLine(cropRect.left, cropRect.top + third,
                cropRect.right, cropRect.top + third, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + 2 * third,
                cropRect.right, cropRect.top + 2 * third, gridPaint);
    }

    private void drawCorner(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, 15, cornerPaint);
    }

    private void drawEdge(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, 10, cornerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragMode = getDragMode(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragMode != DragMode.NONE) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;

                    handleDrag(dx, dy);

                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = DragMode.NONE;
                return true;
        }

        return super.onTouchEvent(event);
    }

    private DragMode getDragMode(float x, float y) {
        // Check corners first
        if (isNear(x, y, cropRect.left, cropRect.top, CORNER_TOUCH_RADIUS)) {
            return DragMode.TOP_LEFT;
        }
        if (isNear(x, y, cropRect.right, cropRect.top, CORNER_TOUCH_RADIUS)) {
            return DragMode.TOP_RIGHT;
        }
        if (isNear(x, y, cropRect.left, cropRect.bottom, CORNER_TOUCH_RADIUS)) {
            return DragMode.BOTTOM_LEFT;
        }
        if (isNear(x, y, cropRect.right, cropRect.bottom, CORNER_TOUCH_RADIUS)) {
            return DragMode.BOTTOM_RIGHT;
        }

        // Check edges
        if (isNearVertical(x, cropRect.left, EDGE_TOUCH_RADIUS) &&
                y >= cropRect.top && y <= cropRect.bottom) {
            return DragMode.LEFT_EDGE;
        }
        if (isNearVertical(x, cropRect.right, EDGE_TOUCH_RADIUS) &&
                y >= cropRect.top && y <= cropRect.bottom) {
            return DragMode.RIGHT_EDGE;
        }
        if (isNearHorizontal(y, cropRect.top, EDGE_TOUCH_RADIUS) &&
                x >= cropRect.left && x <= cropRect.right) {
            return DragMode.TOP_EDGE;
        }
        if (isNearHorizontal(y, cropRect.bottom, EDGE_TOUCH_RADIUS) &&
                x >= cropRect.left && x <= cropRect.right) {
            return DragMode.BOTTOM_EDGE;
        }

        // Check if inside crop rect
        if (cropRect.contains(x, y)) {
            return DragMode.MOVE;
        }

        return DragMode.NONE;
    }

    private boolean isNear(float x, float y, float targetX, float targetY, float radius) {
        float dx = x - targetX;
        float dy = y - targetY;
        return Math.sqrt(dx * dx + dy * dy) <= radius;
    }

    private boolean isNearVertical(float x, float targetX, float radius) {
        return Math.abs(x - targetX) <= radius;
    }

    private boolean isNearHorizontal(float y, float targetY, float radius) {
        return Math.abs(y - targetY) <= radius;
    }

    private void handleDrag(float dx, float dy) {
        RectF newRect = new RectF(cropRect);

        switch (dragMode) {
            case MOVE:
                newRect.offset(dx, dy);
                // Keep within image bounds
                if (newRect.left < imageBounds.left) {
                    newRect.offset(imageBounds.left - newRect.left, 0);
                }
                if (newRect.right > imageBounds.right) {
                    newRect.offset(imageBounds.right - newRect.right, 0);
                }
                if (newRect.top < imageBounds.top) {
                    newRect.offset(0, imageBounds.top - newRect.top);
                }
                if (newRect.bottom > imageBounds.bottom) {
                    newRect.offset(0, imageBounds.bottom - newRect.bottom);
                }
                break;

            case TOP_LEFT:
                newRect.left += dx;
                newRect.top += dy;
                break;
            case TOP_RIGHT:
                newRect.right += dx;
                newRect.top += dy;
                break;
            case BOTTOM_LEFT:
                newRect.left += dx;
                newRect.bottom += dy;
                break;
            case BOTTOM_RIGHT:
                newRect.right += dx;
                newRect.bottom += dy;
                break;
            case LEFT_EDGE:
                newRect.left += dx;
                break;
            case RIGHT_EDGE:
                newRect.right += dx;
                break;
            case TOP_EDGE:
                newRect.top += dy;
                break;
            case BOTTOM_EDGE:
                newRect.bottom += dy;
                break;
        }

        // Validate new rect
        if (newRect.width() >= 100 && newRect.height() >= 100 &&
                newRect.left >= imageBounds.left && newRect.right <= imageBounds.right &&
                newRect.top >= imageBounds.top && newRect.bottom <= imageBounds.bottom) {
            cropRect.set(newRect);
        }
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;

        // Calculate crop rect in bitmap coordinates
        float scaleX = bitmap.getWidth() / imageBounds.width();
        float scaleY = bitmap.getHeight() / imageBounds.height();

        int left = (int)((cropRect.left - imageBounds.left) * scaleX);
        int top = (int)((cropRect.top - imageBounds.top) * scaleY);
        int width = (int)(cropRect.width() * scaleX);
        int height = (int)(cropRect.height() * scaleY);

        // Clamp values
        left = Math.max(0, Math.min(left, bitmap.getWidth() - 1));
        top = Math.max(0, Math.min(top, bitmap.getHeight() - 1));
        width = Math.max(1, Math.min(width, bitmap.getWidth() - left));
        height = Math.max(1, Math.min(height, bitmap.getHeight() - top));

        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }

    public RectF getCropBounds() {
        if (bitmap == null) return null;

        // Return crop bounds as percentages (0-1) of original image
        float scaleX = bitmap.getWidth() / imageBounds.width();
        float scaleY = bitmap.getHeight() / imageBounds.height();

        float left = (cropRect.left - imageBounds.left) * scaleX / bitmap.getWidth();
        float top = (cropRect.top - imageBounds.top) * scaleY / bitmap.getHeight();
        float right = (cropRect.right - imageBounds.left) * scaleX / bitmap.getWidth();
        float bottom = (cropRect.bottom - imageBounds.top) * scaleY / bitmap.getHeight();

        return new RectF(left, top, right, bottom);
    }
}