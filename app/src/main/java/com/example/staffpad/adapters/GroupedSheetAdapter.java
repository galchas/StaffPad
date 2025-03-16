package com.example.staffpad.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.GroupHeader;
import com.example.staffpad.R;
import com.example.staffpad.data_model.SheetMusic;

import java.util.List;

public class GroupedSheetAdapter extends RecyclerView.Adapter<GroupedSheetAdapter.HeaderViewHolder> {
    private List<Object> items;
    private Context context;
    private OnGroupClickListener groupListener;

    public interface OnGroupClickListener {
        void onGroupClick(String groupName, List<SheetMusic> sheets);
    }

    public GroupedSheetAdapter(Context context, List<Object> items) {
        this.context = context;
        this.items = items;
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.groupListener = listener;
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a simple list item for each group
        View view = LayoutInflater.from(context).inflate(R.layout.item_group_header, parent, false);
        return new HeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
        GroupHeader header = (GroupHeader) items.get(position);
        holder.titleView.setText(header.getTitle());
        holder.countView.setText(header.getCount() + " sheets");

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (groupListener != null) {
                groupListener.onGroupClick(header.getTitle(), header.getSheets());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView countView;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.group_title);
            countView = itemView.findViewById(R.id.group_count);
        }
    }
}