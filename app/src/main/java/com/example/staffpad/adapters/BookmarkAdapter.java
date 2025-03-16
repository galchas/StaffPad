package com.example.staffpad.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.R;
import com.example.staffpad.data_model.BookmarkItem;

import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    private List<BookmarkItem> items;

    public BookmarkAdapter(List<BookmarkItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bookmark_item, parent, false);
        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        BookmarkItem item = items.get(position);
        holder.nameView.setText(item.getName());
        holder.pageView.setText("Page " + item.getPage());

        holder.itemView.setOnClickListener(v -> {
            // Navigate to the bookmarked page
            navigateToPage(item.getPage());
        });
    }

    private void navigateToPage(int page) {
        // Implementation for navigating to the specified page
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView nameView;
        TextView pageView;

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.bookmark_name);
            pageView = itemView.findViewById(R.id.bookmark_page);
        }
    }
}