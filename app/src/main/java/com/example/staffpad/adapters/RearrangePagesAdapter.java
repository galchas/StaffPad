package com.example.staffpad.adapters;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.R;

import java.util.ArrayList;
import java.util.List;

public class RearrangePagesAdapter extends RecyclerView.Adapter<RearrangePagesAdapter.VH> {

    private ItemTouchHelper dragHelper;

    public void setDragHelper(ItemTouchHelper helper) {
        this.dragHelper = helper;
    }

    public static class PageItem {
        public int originalIndex; // original PDF page index
        public Bitmap thumbnail;  // may be null until loaded
        public boolean selected;
        public PageItem(int originalIndex, Bitmap thumbnail) {
            this.originalIndex = originalIndex;
            this.thumbnail = thumbnail;
            this.selected = false;
        }
    }

    public interface Listener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<PageItem> items = new ArrayList<>();
    private final Listener listener;
    private boolean selectionMode = false; // when false, taps do not change selection
    private boolean multiSelectEnabled = false; // when true, allow selecting multiple items

    public RearrangePagesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSelectionMode(boolean enabled) {
        if (!enabled) {
            // Leaving selection mode clears any selection
            clearSelection();
        }
        selectionMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void setMultiSelectEnabled(boolean enabled) {
        if (!enabled) {
            // Switching to single-select: collapse to at most one selection
            int first = getFirstSelectedPosition();
            clearSelection();
            if (first >= 0) {
                // keep the first selection only
                safeSelect(first, true);
            }
        }
        multiSelectEnabled = enabled;
        notifySelection();
    }

    public boolean isMultiSelectEnabled() { return multiSelectEnabled; }

    public void setItems(List<PageItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
        notifySelection();
    }

    public List<PageItem> getItems() { return items; }

    public void moveItem(int from, int to) {
        if (from == to) return;
        PageItem item = items.remove(from);
        items.add(to, item);
        notifyItemMoved(from, to);
    }

    public void toggleSelection(int position) {
        if (!selectionMode) return; // selection disabled by default
        if (position < 0 || position >= items.size()) return;

        if (multiSelectEnabled) {
            items.get(position).selected = !items.get(position).selected;
            notifyItemChanged(position);
        } else {
            // single-select behavior
            boolean wasSelected = items.get(position).selected;
            clearSelectionInternal();
            if (!wasSelected) {
                items.get(position).selected = true;
            }
            notifyDataSetChanged();
        }
        notifySelection();
    }

    public void clearSelection() {
        if (clearSelectionInternal()) {
            notifyDataSetChanged();
            notifySelection();
        } else {
            notifySelection();
        }
    }

    private boolean clearSelectionInternal() {
        boolean any = false;
        for (PageItem it : items) {
            if (it.selected) { it.selected = false; any = true; }
        }
        return any;
    }

    private void safeSelect(int position, boolean selected) {
        if (position >= 0 && position < items.size()) {
            items.get(position).selected = selected;
            notifyItemChanged(position);
        }
    }

    private int getFirstSelectedPosition() {
        for (int i = 0; i < items.size(); i++) if (items.get(i).selected) return i;
        return -1;
    }

    public void deleteSelected() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).selected) {
                items.remove(i);
                notifyItemRemoved(i);
            }
        }
        notifySelection();
    }

    public List<Integer> computeOrderMapping() {
        // Return list mapping logical index -> original index
        List<Integer> map = new ArrayList<>();
        for (PageItem it : items) map.add(it.originalIndex);
        return map;
    }

    public List<Integer> computeDeletedOriginals(int originalTotalCount) {
        // Any original index not present in items is considered deleted
        boolean[] present = new boolean[originalTotalCount];
        for (PageItem it : items) {
            if (it.originalIndex >= 0 && it.originalIndex < originalTotalCount)
                present[it.originalIndex] = true;
        }
        List<Integer> del = new ArrayList<>();
        for (int i = 0; i < originalTotalCount; i++) if (!present[i]) del.add(i);
        return del;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page_thumbnail, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PageItem it = items.get(position);
        h.pageNumber.setText(String.valueOf(position + 1));
        h.checkBox.setChecked(it.selected);
        if (it.thumbnail != null) {
            h.image.setImageBitmap(it.thumbnail);
        } else {
            h.image.setImageResource(android.R.color.darker_gray);
        }
        // Click to select only when selection mode is enabled
        if (selectionMode) {
            h.itemView.setOnClickListener(v -> toggleSelection(h.getBindingAdapterPosition()));
        } else {
            h.itemView.setOnClickListener(null);
        }
        // Start drag immediately on touch-down when not in selection mode
        h.itemView.setOnTouchListener((v, e) -> {
            if (!selectionMode && e.getActionMasked() == MotionEvent.ACTION_DOWN && dragHelper != null) {
                dragHelper.startDrag(h);
                return true;
            }
            return false;
        });
        // Keep long press available for accessibility but not used to toggle selection
        h.itemView.setOnLongClickListener(v -> false);
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        CheckBox checkBox;
        TextView pageNumber;
        public VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageThumbnail);
            checkBox = itemView.findViewById(R.id.checkBox);
            pageNumber = itemView.findViewById(R.id.pageNumber);
        }
    }

    private void notifySelection() {
        if (listener == null) return;
        int count = 0;
        for (PageItem it : items) if (it.selected) count++;
        listener.onSelectionChanged(count);
    }
}
