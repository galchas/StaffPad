package com.example.staffpad;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.data_model.ToolItem;
import com.example.staffpad.viewmodel.SheetViewModel;

import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ToolViewHolder> {

    private final List<ToolItem> items;
    private SheetViewModel sheetViewModel;
    private LifecycleOwner lifecycleOwner;

    public ToolAdapter(List<ToolItem> items) {
        this.items = items;
    }

    // Add this method to set the ViewModel
    public void setSheetViewModel(SheetViewModel viewModel, LifecycleOwner owner) {
        this.sheetViewModel = viewModel;
        this.lifecycleOwner = owner;
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tool_item, parent, false);
        return new ToolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        ToolItem item = items.get(position);
        holder.nameView.setText(item.getName());
        holder.iconView.setImageResource(item.getIconResId());

        holder.itemView.setOnClickListener(v -> {
            // Activate the selected tool
            activateTool(v, item);
        });
    }

    private void activateTool(View view, ToolItem item) {
        Context context = view.getContext();

        if (item.getName().equals("Crop")) {
            // Launch crop activity for currently open sheet
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;

                // Get the SheetViewModel
                SheetViewModel viewModel = new ViewModelProvider(activity).get(SheetViewModel.class);

                // Observe the selected sheet
                viewModel.getSelectedSheet().observe(activity, sheet -> {
                    if (sheet != null && sheet.getId() > 0) {
                        // We have a valid sheet selected
                        Intent intent = new Intent(context, CropActivity.class);
                        intent.putExtra(CropActivity.EXTRA_SHEET_ID, sheet.getId());
                        intent.putExtra(CropActivity.EXTRA_PAGE_NUMBER, 0); // Start at page 0
                        activity.startActivity(intent);

                        // Remove observer after launching
                        viewModel.getSelectedSheet().removeObservers(activity);
                    } else {
                        // No sheet selected
                        Toast.makeText(context, "Please open a sheet first to use the crop tool",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        } else {
            // Other tools
            Toast.makeText(context, item.getName() + " tool activated", Toast.LENGTH_SHORT).show();
        }
    }

    private void activateCropTool(View view, long sheetId) {
        Log.d("ToolAdapter", "Activating crop tool for sheet ID: " + sheetId);
        Toast.makeText(view.getContext(), "Crop tool activated", Toast.LENGTH_SHORT).show();

        // TODO: Implement actual crop functionality
        // You'll need to get the SheetDetailFragment and enable crop mode
    }

    private void activateAnnotateTool(View view, long sheetId) {
        Log.d("ToolAdapter", "Activating annotate tool for sheet ID: " + sheetId);
        Toast.makeText(view.getContext(), "Annotate tool activated", Toast.LENGTH_SHORT).show();

        // TODO: Implement actual annotation functionality
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ToolViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView nameView;

        public ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.tool_icon);
            nameView = itemView.findViewById(R.id.tool_name);
        }
    }
}