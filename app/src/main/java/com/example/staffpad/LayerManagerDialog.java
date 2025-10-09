package com.example.staffpad;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.PageLayerDao;
import com.example.staffpad.database.PageLayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LayerManagerDialog extends DialogFragment {
    private static final String ARG_SHEET_ID = "arg_sheet_id";
    private static final String ARG_PAGE_NUMBER = "arg_page_number";

    public interface OnLayersChangedListener {
        void onLayersChanged();
    }

    private OnLayersChangedListener onLayersChangedListener;

    public static LayerManagerDialog newInstance(long sheetId, int pageNumber) {
        LayerManagerDialog dialog = new LayerManagerDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_SHEET_ID, sheetId);
        args.putInt(ARG_PAGE_NUMBER, pageNumber);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnLayersChangedListener(OnLayersChangedListener listener) {
        this.onLayersChangedListener = listener;
    }

    private long sheetId;
    private int pageNumber;
    private PageLayerDao dao;
    private LayerAdapter adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            sheetId = getArguments().getLong(ARG_SHEET_ID, -1);
            pageNumber = getArguments().getInt(ARG_PAGE_NUMBER, 0);
        }

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_layer_manager, null, false);
        RecyclerView recyclerView = content.findViewById(R.id.layers_recycler);
        Button addButton = content.findViewById(R.id.add_layer_button);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LayerAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        AppDatabase db = AppDatabase.getDatabase(requireContext().getApplicationContext());
        dao = db.pageLayerDao();

        // Observe on the Activity lifecycle to avoid background-thread observe errors
        dao.getAllLayersForPage(sheetId, pageNumber).observe(requireActivity(), layers -> {
            if (layers == null) return;
            adapter.setItems(new ArrayList<>(layers));
        });

        addButton.setOnClickListener(v -> addNewLayer());

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("Layer Manager")
                .setView(content)
                .setPositiveButton("Close", (d, w) -> dismiss());

        return builder.create();
    }

    private void addNewLayer() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int maxOrder = dao.getMaxOrderIndex(sheetId, pageNumber);
                int nextOrder = maxOrder + 1;
                PageLayerEntity layer = new PageLayerEntity(sheetId, pageNumber, "Layer " + nextOrder, "ANNOTATION");
                layer.setOrderIndex(nextOrder);
                layer.setActive(true);
                layer.setModifiedAt(System.currentTimeMillis());
                dao.insert(layer);
                if (onLayersChangedListener != null) {
                    requireActivity().runOnUiThread(() -> onLayersChangedListener.onLayersChanged());
                }
            } catch (Exception ignored) { }
        });
    }

    private void toggleActive(PageLayerEntity item, boolean active) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            item.setActive(active);
            dao.update(item);
            if (onLayersChangedListener != null) {
                requireActivity().runOnUiThread(() -> onLayersChangedListener.onLayersChanged());
            }
        });
    }

    private void deleteLayer(PageLayerEntity item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            dao.delete(item);
            if (onLayersChangedListener != null) {
                requireActivity().runOnUiThread(() -> onLayersChangedListener.onLayersChanged());
            }
        });
    }

    private void moveLayer(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || adapter.items == null) return;
        if (fromPosition >= adapter.items.size() || toPosition >= adapter.items.size()) return;
        List<PageLayerEntity> list = new ArrayList<>(adapter.items);
        Collections.swap(list, fromPosition, toPosition);
        // Reassign orderIndex sequentially
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (int i = 0; i < list.size(); i++) {
                PageLayerEntity e = list.get(i);
                e.setOrderIndex(i);
                dao.update(e);
            }
            if (onLayersChangedListener != null) {
                requireActivity().runOnUiThread(() -> onLayersChangedListener.onLayersChanged());
            }
        });
    }

    private class LayerAdapter extends RecyclerView.Adapter<LayerAdapter.LayerVH> {
        private List<PageLayerEntity> items;
        LayerAdapter(List<PageLayerEntity> items) { this.items = items; }
        void setItems(List<PageLayerEntity> newItems) { this.items = newItems; notifyDataSetChanged(); }

        @NonNull
        @Override
        public LayerVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layer, (android.view.ViewGroup) parent, false);
            return new LayerVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LayerVH holder, int position) {
            PageLayerEntity item = items.get(position);
            holder.name.setText(item.getLayerName());
            holder.active.setChecked(item.isActive());
            holder.type.setText(item.getLayerType());

            holder.active.setOnCheckedChangeListener((buttonView, isChecked) -> toggleActive(item, isChecked));
            holder.delete.setOnClickListener(v -> deleteLayer(item));
            holder.up.setOnClickListener(v -> moveLayer(holder.getAdapterPosition(), holder.getAdapterPosition() - 1));
            holder.down.setOnClickListener(v -> moveLayer(holder.getAdapterPosition(), holder.getAdapterPosition() + 1));
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        class LayerVH extends RecyclerView.ViewHolder {
            TextView name;
            TextView type;
            CheckBox active;
            ImageButton up;
            ImageButton down;
            ImageButton delete;
            LayerVH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.layer_name);
                type = itemView.findViewById(R.id.layer_type);
                active = itemView.findViewById(R.id.layer_active_checkbox);
                up = itemView.findViewById(R.id.button_up);
                down = itemView.findViewById(R.id.button_down);
                delete = itemView.findViewById(R.id.button_delete);
            }
        }
    }
}
