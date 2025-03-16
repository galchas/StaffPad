package com.example.staffpad;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.data_model.ToolItem;

import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ToolViewHolder> {

    private final List<ToolItem> items;

    public ToolAdapter(List<ToolItem> items) {
        this.items = items;
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
        // Implementation for activating the selected tool
        Toast.makeText(view.getContext(), item.getName() + " tool activated", Toast.LENGTH_SHORT).show();
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