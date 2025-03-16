package com.example.staffpad.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.R;
import com.example.staffpad.SheetMetadataDialog;
import com.example.staffpad.data_model.SheetMusic;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.viewmodel.SheetViewModel;

import java.util.ArrayList;
import java.util.List;

public class SheetMusicAdapter extends RecyclerView.Adapter<SheetMusicAdapter.SheetMusicViewHolder> {

    private List<SheetMusic> items;

    public SheetMusicAdapter(Activity activity, List<SheetMusic> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    @NonNull
    @Override
    public SheetMusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d("SheetMusicAdapter", "Creating view holder");
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.sheet_music_item, parent, false);
        return new SheetMusicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SheetMusicViewHolder holder, int position) {
        Log.d("SheetMusicAdapter", "Binding view at position " + position);

        SheetMusic item = items.get(position);
        holder.titleView.setText(item.getTitle());

        // Handle potentially null composers
        String composers = item.getComposers();
        holder.composerView.setText(composers != null && !composers.isEmpty() ? composers : "Unknown composer");

        holder.infoButton.setTag(item);
        holder.itemView.setTag(item);

        // Make sure view has correct background
        holder.itemView.setBackgroundResource(android.R.color.white);

        holder.infoButton.setOnClickListener(v -> {
            showSheetPropertiesDialog(v, (SheetMusic) v.getTag());
        });

        holder.itemView.setOnClickListener(v -> {
            // Open the sheet music
            openSheetMusic((SheetMusic)v.getTag());
        });

        // Debug log to verify we're binding something reasonable
        Log.d("SheetMusicAdapter", "Bound sheet: " + item.getTitle() + " at position " + position);
    }

    private void showSheetPropertiesDialog(View view, SheetMusic item) {
        // Create and show the dialog directly with the SheetMusic object
        SheetMetadataDialog dialog = SheetMetadataDialog.newInstance(item);
        dialog.setListener(updatedSheet -> {
            // Convert the updated SheetMusic back to SheetEntity for the database
            SheetEntity updatedEntity = convertSheetMusicToSheetEntity(updatedSheet);
            // Get ViewModel from activity context
            SheetViewModel viewModel = new ViewModelProvider((AppCompatActivity)view.getContext())
                    .get(SheetViewModel.class);
            viewModel.updateSheet(updatedEntity);
        });

        // Show the dialog
        dialog.show(((AppCompatActivity)view.getContext()).getSupportFragmentManager(), "metadata_dialog");
    }

    private SheetEntity convertSheetMusicToSheetEntity(SheetMusic sheetMusic) {
        SheetEntity entity = new SheetEntity(
                sheetMusic.getTitle(),
                sheetMusic.getFilename(),
                "", // you may need to provide filePath from somewhere
                sheetMusic.getFileSize(),
                sheetMusic.getPageCount()
        );
        entity.setId(sheetMusic.getId());
        entity.setComposers(sheetMusic.getComposers());
        entity.setGenres(sheetMusic.getGenres());
        entity.setLabels(sheetMusic.getLabels());
        entity.setReferences(sheetMusic.getReferences());
        entity.setRating(sheetMusic.getRating());
        entity.setKey(sheetMusic.getKey());

        return entity;
    }
    @SuppressLint("NotifyDataSetChanged")
    public void updateSheets(List<SheetMusic> newSheets) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.clear();
        if (newSheets != null) {
            items.addAll(newSheets);
        }
        notifyDataSetChanged();
    }

    private void openSheetMusic(SheetMusic sheet) {
        if (itemClickListener != null) {
            itemClickListener.onSheetItemClick(sheet);
        }
    }

    private OnSheetItemClickListener itemClickListener;

    public void setOnSheetItemClickListener(OnSheetItemClickListener listener) {
        this.itemClickListener = listener;
    }
    public interface OnSheetItemClickListener {
        void onSheetItemClick(SheetMusic sheet);
    }

    @Override
    public int getItemCount() {
        if (items == null) {
            return 0;
        }
        return items.size();
    }


    public interface OnSheetClickListener {
        void onSheetClick(SheetEntity sheet);
        void onSheetLongClick(SheetEntity sheet, View view);
    }


    public static class SheetMusicViewHolder extends RecyclerView.ViewHolder {
        public TextView titleView;
        public TextView composerView;
        public ImageButton infoButton;

        public SheetMusicViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.sheet_title);
            composerView = itemView.findViewById(R.id.sheet_composer);
            infoButton = itemView.findViewById(R.id.info_button);
        }
    }
}