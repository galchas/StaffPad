package com.example.staffpad;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/**
 * Handler for resizing sidebars by dragging their edge.
 * Allows users to customize the width of sidebars by dragging the resize handle.
 */
public class SidebarResizeHandler implements View.OnTouchListener {

    private final View sidebarView;
    private final int minWidth;
    private final int maxWidth;
    private float lastX;
    private boolean isLeftSidebar;

    /**
     * Creates a new resize handler
     * @param sidebarView The sidebar view to resize
     * @param minWidth Minimum allowed width in pixels
     * @param maxWidth Maximum allowed width in pixels
     * @param isLeftSidebar Whether this is a left sidebar (true) or right sidebar (false)
     */
    public SidebarResizeHandler(View sidebarView, int minWidth, int maxWidth, boolean isLeftSidebar) {
        this.sidebarView = sidebarView;
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.isLeftSidebar = isLeftSidebar;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getRawX();
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - lastX;

                // Adjust width based on sidebar position (left or right)
                int currentWidth = sidebarView.getWidth();
                int newWidth;

                if (isLeftSidebar) {
                    newWidth = currentWidth + (int)deltaX;
                } else {
                    newWidth = currentWidth - (int)deltaX;
                }

                // Ensure width stays within bounds
                newWidth = Math.max(minWidth, Math.min(newWidth, maxWidth));

                // Apply the new width
                if (newWidth != currentWidth) {
                    android.view.ViewGroup.LayoutParams params = sidebarView.getLayoutParams();
                    params.width = newWidth;
                    sidebarView.setLayoutParams(params);
                }

                lastX = event.getRawX();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }

        return false;
    }
}