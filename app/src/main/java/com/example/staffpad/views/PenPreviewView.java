package com.example.staffpad.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Preview view that draws a centered horizontal line over a checkerboard background
 * so users can notice opacity changes easily.
 */
public class PenPreviewView extends View {
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private BitmapShader checkerShader;
    private int lastTileSizePx = 0;

    public PenPreviewView(Context context) {
        super(context);
        init();
    }

    public PenPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PenPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(6f);
        strokePaint.setAlpha(255);
        setWillNotDraw(false);
        buildCheckerIfNeeded();
    }

    private void buildCheckerIfNeeded() {
        int tile = Math.max(4, (int) (8f * getResources().getDisplayMetrics().density));
        if (tile == lastTileSizePx && checkerShader != null) return;
        lastTileSizePx = tile;

        int size = tile * 2; // 2x2 tiles
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        int light = 0xFFFFFFFF; // white
        int dark = 0xFFCCCCCC;  // light gray
        // Top-left (dark)
        p.setColor(dark); c.drawRect(0, 0, tile, tile, p);
        // Top-right (light)
        p.setColor(light); c.drawRect(tile, 0, size, tile, p);
        // Bottom-left (light)
        p.setColor(light); c.drawRect(0, tile, tile, size, p);
        // Bottom-right (dark)
        p.setColor(dark); c.drawRect(tile, tile, size, size, p);

        checkerShader = new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        bgPaint.setShader(checkerShader);
        bgPaint.setAntiAlias(false);
    }

    public void setStrokeColor(int color) {
        strokePaint.setColor(color);
        invalidate();
    }

    public void setStrokeAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        invalidate();
    }

    public void setStrokeWidthPx(float widthPx) {
        if (widthPx < 1f) widthPx = 1f;
        strokePaint.setStrokeWidth(widthPx);
        invalidate();
    }

    public int getStrokeColor() { return strokePaint.getColor(); }
    public int getStrokeAlpha() { return strokePaint.getAlpha(); }
    public float getStrokeWidthPx() { return strokePaint.getStrokeWidth(); }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildCheckerIfNeeded();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();

        // Draw checkerboard background
        if (checkerShader == null) buildCheckerIfNeeded();
        if (checkerShader != null) {
            c.drawRect(new RectF(0, 0, w, h), bgPaint);
        } else {
            // Fallback solid background
            c.drawColor(0xFFEFEFEF);
        }

        // Draw stroke preview line
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        int cy = h / 2;
        int left = pad;
        int right = Math.max(left + 1, w - pad);
        c.drawLine(left, cy, right, cy, strokePaint);
    }
}
