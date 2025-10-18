package com.example.staffpad;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.data_model.ToolItem;

import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final List<ToolItem> items;
    private OnToolClickListener listener;

    public interface OnToolClickListener {
        void onToolClick(String toolName);
    }

    public ToolAdapter(List<ToolItem> items) {
        this.items = items;
    }

    public void setOnToolClickListener(OnToolClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.tool_section_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.tool_item, parent, false);
            return new ToolViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ToolItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).titleView.setText(item.getName());
            holder.itemView.setOnClickListener(null);
        } else if (holder instanceof ToolViewHolder) {
            ToolViewHolder vh = (ToolViewHolder) holder;
            vh.nameView.setText(item.getName());
            if (item.getIconResId() != 0) {
                vh.iconView.setVisibility(View.VISIBLE);
                vh.iconView.setImageResource(item.getIconResId());
            } else {
                vh.iconView.setVisibility(View.INVISIBLE);
            }

            holder.itemView.setOnClickListener(v -> {
                String toolName = item.getName();

                if (listener != null) {
                    listener.onToolClick(toolName);
                } else {
                    activateTool(v, item);
                }
            });
        }
    }

    private void activateTool(View view, ToolItem item) {
        Toast.makeText(view.getContext(), item.getName() + " tool activated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ToolViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView nameView;

        public ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.tool_icon);
            nameView = itemView.findViewById(R.id.tool_name);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.section_title);
        }
    }
}