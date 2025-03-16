package com.example.staffpad;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.adapters.SheetMusicAdapter;
import com.example.staffpad.data_model.SheetMusic;

import java.util.List;

public class GroupDetailFragment extends Fragment {
    private static final String ARG_GROUP_NAME = "group_name";
    private static final String ARG_FILTER_TYPE = "filter_type";
    private RecyclerView recyclerView;
    private List<SheetMusic> sheets;
    private String groupName;
    private String filterType;

    public static GroupDetailFragment newInstance(String groupName, List<SheetMusic> sheets, String filterType) {
        GroupDetailFragment fragment = new GroupDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_NAME, groupName);
        args.putString(ARG_FILTER_TYPE, filterType);
        fragment.setArguments(args);
        fragment.sheets = sheets;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_detail, container, false);

        // Setup header with back button
        TextView titleText = view.findViewById(R.id.group_title);
        ImageButton backButton = view.findViewById(R.id.back_button);

        // Get args
        if (getArguments() != null) {
            groupName = getArguments().getString(ARG_GROUP_NAME);
            filterType = getArguments().getString(ARG_FILTER_TYPE);
            titleText.setText(filterType + ": " + groupName);
        }

        // Setup back button
        backButton.setOnClickListener(v -> {
            // Go back to group list
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.showGroupList();
            }
        });

        // Setup recycler view
        recyclerView = view.findViewById(R.id.sheets_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Set adapter
        SheetMusicAdapter adapter = new SheetMusicAdapter(getActivity(), sheets);
        adapter.setOnSheetItemClickListener(sheet -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onSheetItemClick(sheet);
            }
        });
        recyclerView.setAdapter(adapter);

        return view;
    }
}