package com.example.staffpad.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.R;
import com.example.staffpad.data_model.Library;

import java.util.ArrayList;
import java.util.List;

// Create this new adapter class
public class LibrarySelectionAdapter extends RecyclerView.Adapter<LibrarySelectionAdapter.LibraryViewHolder> {

    private List<Library> libraries;
    private LibrarySelectionListener listener;

    public interface LibrarySelectionListener {
        void onLibrarySelected(Library library);
    }

    public LibrarySelectionAdapter(List<Library> libraries, LibrarySelectionListener listener) {
        this.libraries = libraries != null ? libraries : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public LibraryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_library_selection, parent, false);
        return new LibraryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryViewHolder holder, int position) {
        Library library = libraries.get(position);

        holder.nameTextView.setText(library.getName());
        holder.countTextView.setText(library.getSheets().size() + " sheets");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLibrarySelected(library);
            }
        });
    }

    @Override
    public int getItemCount() {
        return libraries.size();
    }

    static class LibraryViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView countTextView;

        public LibraryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.library_name);
            countTextView = itemView.findViewById(R.id.sheet_count);
        }
    }
}