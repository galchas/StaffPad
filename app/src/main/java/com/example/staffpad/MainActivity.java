package com.example.staffpad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.adapters.BookmarkAdapter;
import com.example.staffpad.adapters.GroupedSheetAdapter;
import com.example.staffpad.adapters.LibrarySelectionAdapter;
import com.example.staffpad.adapters.SheetMusicAdapter;
import com.example.staffpad.data_model.BookmarkItem;
import com.example.staffpad.data_model.Library;
import com.example.staffpad.data_model.SheetMusic;
import com.example.staffpad.data_model.ToolItem;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.LibraryEntity;
import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.database.repository.LibraryRepository;
import com.example.staffpad.database.repository.SheetMusicRepository;
import com.example.staffpad.viewmodel.SheetViewModel;

import com.example.staffpad.utils.SharedPreferencesHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MainActivity extends AppCompatActivity {
    public enum FilterType {
        NONE, COMPOSERS, TAGS, LABELS, GENRES
    }
    public FilterType getCurrentFilter() {
        return currentFilter;
    }
    private SharedPreferencesHelper preferencesHelper;

    public void setCurrentFilter(FilterType currentFilter) {
        this.currentFilter = currentFilter;
    }

    private FilterType currentFilter = FilterType.NONE;
    private Library currentLibrary = null; // Track the currently selected library
    private MotionLayout motionLayout;
    private View scrimOverlay;

    private boolean sidebarLocked = false;

    // Sidebars
    private LinearLayout librarySidebar;
    private ConstraintLayout bookmarksSidebar;
    private ConstraintLayout setlistSidebar;
    private ConstraintLayout toolsSidebar;

    // Resize handles
    private View libraryResizeHandle;
    private View bookmarksResizeHandle;
    private View setlistResizeHandle;
    private View toolsResizeHandle;

    // RecyclerViews
    private RecyclerView libraryRecyclerView;
    private RecyclerView bookmarksRecyclerView;
    private RecyclerView setlistRecyclerView;
    private RecyclerView toolsRecyclerView;

    // Toolbar buttons
    private ImageButton libraryButton;
    private ImageButton bookmarksButton;
    private ImageButton setlistButton;
    private ImageButton toolsButton;
    private ImageButton metronomeButton;
    private ImageButton searchButton;

    // Dialogs
    private CardView pageOptionsMenu;
    private CardView searchDialog;

    // State tracking
    private String currentSidebar = null;
    private boolean isToolbarVisible = true;

    AppDatabase database;
    SheetViewModel sheetViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        database = AppDatabase.getDatabase(getApplicationContext());
        sheetViewModel = new SheetViewModel(getApplication());
        preferencesHelper = new SharedPreferencesHelper(this);

        // Initialize views
        initializeViews();

        // Set up listeners
        setupListeners();

        // Set up recycler views
        setupRecyclerViews();

        // Debug sheet data - add this line
        debugSheetData();

        // Initialize sidebar positions when layout is ready
        motionLayout.post(() -> {
            // Calculate proper initial positions for sidebars
            int libraryWidth = librarySidebar.getMeasuredWidth();
            int bookmarksWidth = bookmarksSidebar.getMeasuredWidth();
            int setlistWidth = setlistSidebar.getMeasuredWidth();
            int toolsWidth = toolsSidebar.getMeasuredWidth();

            // Position all sidebars off-screen
            librarySidebar.setTranslationX(-libraryWidth);
            bookmarksSidebar.setTranslationX(-bookmarksWidth);
            setlistSidebar.setTranslationX(-setlistWidth);
            toolsSidebar.setTranslationX(toolsWidth);

            // Make sure scrim is hidden
            scrimOverlay.setVisibility(View.GONE);
            scrimOverlay.setAlpha(0);

            // Debug logs to verify measurements
            Log.d("MainActivity", "Library width: " + libraryWidth);
            Log.d("MainActivity", "Bookmarks width: " + bookmarksWidth);
            Log.d("MainActivity", "Setlist width: " + setlistWidth);
            Log.d("MainActivity", "Tools width: " + toolsWidth);
        });

        if (savedInstanceState == null) {
            if (preferencesHelper.hasLastViewInformation()) {
                long lastSheetId = preferencesHelper.getLastViewedSheetId();
                int lastPageNumber = preferencesHelper.getLastViewedPageNumber();

                Log.d("MainActivity", "Restoring last viewed sheet: " + lastSheetId +
                        ", page: " + lastPageNumber);

                // Load the last viewed sheet
                restoreLastViewedSheet(lastSheetId, lastPageNumber);
            } else {
                // No last view information, show empty detail view
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.content_container, new SheetDetailFragment())
                        .commit();
            }
        }

    }

    private void restoreLastViewedSheet(long sheetId, int pageNumber) {
        // Create a progress dialog for loading
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading last viewed sheet...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Use the SheetViewModel to get the sheet by ID
        sheetViewModel.getSheetMusicById(sheetId).observe(this, sheetMusic -> {
            progressDialog.dismiss();

            if (sheetMusic != null) {
                Log.d("MainActivity", "Found last sheet: " + sheetMusic.getTitle());

                // Create detail fragment with the sheet ID
                SheetDetailFragment detailFragment = SheetDetailFragment.newInstance(sheetId);

                // Replace or add the fragment
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.content_container, detailFragment)
                        .commit();

                // Set the current page after fragment has been created
                new Handler().postDelayed(() -> {
                    SheetDetailFragment fragment = (SheetDetailFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.content_container);

                    if (fragment != null) {
                        fragment.setCurrentPage(pageNumber);
                    }
                }, 300); // Short delay to ensure fragment is ready

                // Update toolbar title
                updateToolbarTitle(sheetMusic.getTitle());

                // Update ViewModel's selected sheet
                sheetViewModel.selectSheet(sheetId);
            } else {
                // Sheet not found (might have been deleted)
                Log.w("MainActivity", "Last viewed sheet not found: " + sheetId);

                // Clear the preferences and show empty detail
                preferencesHelper.clearAllPreferences();

                // Show default fragment
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.content_container, new SheetDetailFragment())
                        .commit();
            }
        });
    }

    private void initializeViews() {
        motionLayout = findViewById(R.id.motionLayout);
        scrimOverlay = findViewById(R.id.scrim_overlay);

        // Sidebars
        librarySidebar = findViewById(R.id.library_sidebar);
        bookmarksSidebar = findViewById(R.id.bookmarks_sidebar);
        setlistSidebar = findViewById(R.id.setlist_sidebar);
        toolsSidebar = findViewById(R.id.tools_sidebar);

        // Resize handles
        libraryResizeHandle = findViewById(R.id.library_resize_handle);
        bookmarksResizeHandle = findViewById(R.id.bookmarks_resize_handle);
        setlistResizeHandle = findViewById(R.id.setlist_resize_handle);
        toolsResizeHandle = findViewById(R.id.tools_resize_handle);

        // RecyclerViews
        libraryRecyclerView = findViewById(R.id.library_recycler_view);
        libraryRecyclerView.setOnTouchListener(null); // Remove any blocking touch listener
        libraryRecyclerView.setClickable(true);
        libraryRecyclerView.setFocusable(true);

        bookmarksRecyclerView = findViewById(R.id.bookmarks_recycler_view);
        setlistRecyclerView = findViewById(R.id.setlist_recycler_view);
        toolsRecyclerView = findViewById(R.id.tools_recycler_view);

        // Toolbar buttons
        libraryButton = findViewById(R.id.library_button);
        bookmarksButton = findViewById(R.id.bookmarks_button);
        setlistButton = findViewById(R.id.setlist_button);
        toolsButton = findViewById(R.id.tools_button);
        metronomeButton = findViewById(R.id.metronome_button);
        searchButton = findViewById(R.id.search_button);

        // Dialogs
        pageOptionsMenu = findViewById(R.id.page_options_menu);
        searchDialog = findViewById(R.id.search_dialog);

        // Setup top library menu buttons
        setupLibraryMenuButtons();

        // Setup bookmarks menu buttons
        setupBookmarksMenuButtons();

        // Setup setlist menu buttons
        setupSetlistMenuButtons();
        librarySidebar.setOnClickListener(null);

        scrimOverlay.setOnClickListener(v -> {
            // Check if we're showing group details
            if (findViewById(R.id.library_navigation_header) != null) {
                Log.d("MainActivity", "In group view, keeping sidebar open");
                // Do nothing, sidebar should stay open
            } else {
                // Close sidebars normally
                closeSidebars();
            }
        });
    }

    // Add this method to your MainActivity class

    private void clearFilterButtons() {
        Button composersButton = findViewById(R.id.composers_button);
        Button genresButton = findViewById(R.id.genres_button);
        Button tagsButton = findViewById(R.id.tags_button);
        Button labelsButton = findViewById(R.id.labels_button);

        composersButton.setSelected(false);
        genresButton.setSelected(false);
        tagsButton.setSelected(false);
        labelsButton.setSelected(false);

        composersButton.setBackgroundResource(android.R.drawable.btn_default);
        genresButton.setBackgroundResource(android.R.drawable.btn_default);
        tagsButton.setBackgroundResource(android.R.drawable.btn_default);
        labelsButton.setBackgroundResource(android.R.drawable.btn_default);
    }

    private void setupLibraryMenuButtons() {
        TextView libraryButton = findViewById(R.id.library_button_text);
        TextView importButton = findViewById(R.id.import_button_text);
        TextView editButton = findViewById(R.id.edit_button_text);

        Button composersButton = findViewById(R.id.composers_button);
        Button genresButton = findViewById(R.id.genres_button);
        Button tagsButton = findViewById(R.id.tags_button);
        Button labelsButton = findViewById(R.id.labels_button);

        libraryButton.setOnClickListener(v -> {
            clearLibraryButtonsSelection();
            libraryButton.setTextColor(getResources().getColor(android.R.color.black));
            // Show library selection dialog
            showLibrarySelectionDialog();
        });

        importButton.setOnClickListener(v -> {
            clearLibraryButtonsSelection();
            importButton.setTextColor(getResources().getColor(android.R.color.black));
            showAddSheetDialog();
        });

        editButton.setOnClickListener(v -> {
            clearLibraryButtonsSelection();
            editButton.setTextColor(getResources().getColor(android.R.color.black));
            // Enable edit mode
        });

        // Set up category filter buttons with background selectors to show active state
        composersButton.setBackgroundResource(android.R.drawable.btn_default);
        genresButton.setBackgroundResource(android.R.drawable.btn_default);
        tagsButton.setBackgroundResource(android.R.drawable.btn_default);
        labelsButton.setBackgroundResource(android.R.drawable.btn_default);

        composersButton.setOnClickListener(v -> {
            // Add logging to see what's happening
            Log.d("MainActivity", "Composers filter clicked");
            clearFilterButtons();
            composersButton.setSelected(true);
            composersButton.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            currentFilter = FilterType.COMPOSERS;
            updateLibraryWithFilter();
        });

        genresButton.setOnClickListener(v -> {
            Log.d("MainActivity", "Genres filter clicked");
            clearFilterButtons();
            genresButton.setSelected(true);
            genresButton.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            currentFilter = FilterType.GENRES;
            updateLibraryWithFilter();
        });

        tagsButton.setOnClickListener(v -> {
            Log.d("MainActivity", "Tags filter clicked");
            clearFilterButtons();
            tagsButton.setSelected(true);
            tagsButton.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            currentFilter = FilterType.TAGS;
            updateLibraryWithFilter();
        });

        labelsButton.setOnClickListener(v -> {
            Log.d("MainActivity", "Labels filter clicked");
            clearFilterButtons();
            labelsButton.setSelected(true);
            labelsButton.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            currentFilter = FilterType.LABELS;
            updateLibraryWithFilter();
        });
    }

    private void updateLibraryWithFilter() {
        Log.d("MainActivity", "Updating library with filter: " + currentFilter);

        // Ensure the library sidebar is open when applying filters
        if (currentSidebar == null || !currentSidebar.equals("library")) {
            Log.d("MainActivity", "Opening library sidebar to apply filter");
            toggleSidebar("library");
        }

        // Use the LiveData approach to avoid main thread database access
        if (currentLibrary != null) {
            Log.d("MainActivity", "Using current library with " +
                    (currentLibrary.getSheets() != null ? currentLibrary.getSheets().size() : 0) + " sheets");
            // We already have the sheets from the library
            applyFilterToSheets(currentLibrary.getSheets());
        } else {
            Log.d("MainActivity", "Fetching all sheets from repository");
            // Need to fetch all sheets from repository using LiveData
            SheetMusicRepository repository = new SheetMusicRepository(getApplication());
            repository.getAllSheetMusicWithRelations().observe(this, sheetMusicList -> {
                Log.d("MainActivity", "Received " +
                        (sheetMusicList != null ? sheetMusicList.size() : 0) + " sheets from repository");
                if (sheetMusicList != null) {
                    // Check if we have data, if not, show a message
                    if (sheetMusicList.isEmpty()) {
                        Toast.makeText(this, "No sheet music available. Try adding some first.", Toast.LENGTH_LONG).show();

                        // Reset to default view
                        currentFilter = FilterType.NONE;
                        clearFilterButtons();

                        // Show empty adapter
                        libraryRecyclerView.setAdapter(new SheetMusicAdapter(this, new ArrayList<>()));
                    } else {
                        // Apply filter to the sheets
                        applyFilterToSheets(sheetMusicList);
                    }
                }
            });
        }
    }
    private void applyFilterToSheets(List<SheetMusic> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            Log.d("MainActivity", "No sheets to filter");
            // Set empty adapter to show no data
            libraryRecyclerView.setAdapter(new SheetMusicAdapter(this, new ArrayList<>()));
            return;
        }

        Log.d("MainActivity", "Applying filter " + currentFilter + " to " + sheets.size() + " sheets");

        // Reset any existing header
        removeNavigationHeader();

        // Unlock sidebar if it was locked
        sidebarLocked = false;

        // Switch from list view to a grouped view based on filter
        switch (currentFilter) {
            case COMPOSERS:
                Log.d("MainActivity", "Setting up composers view");
                setupGroupedLibraryView(sheets, SheetMusic::getComposers, "Composers");
                break;

            case GENRES:
                Log.d("MainActivity", "Setting up genres view");
                setupGroupedLibraryView(sheets, SheetMusic::getGenres, "Genres");
                break;

            case TAGS:
                Log.d("MainActivity", "Setting up tags view");
                setupGroupedLibraryView(sheets, sheet -> {
                    if (sheet.getTags() != null && !sheet.getTags().isEmpty()) {
                        return String.join(", ", sheet.getTags());
                    }
                    return "Uncategorized";
                }, "Tags");
                break;

            case LABELS:
                Log.d("MainActivity", "Setting up labels view");
                setupGroupedLibraryView(sheets, sheet -> {
                    if (sheet.getLabels() != null && !sheet.getLabels().isEmpty()) {
                        return sheet.getLabels();
                    }
                    return "Uncategorized";
                }, "Labels");
                break;

            case NONE:
            default:
                Log.d("MainActivity", "Setting up default view");
                // Just show the normal list
                SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
                adapter.setOnSheetItemClickListener(this::onSheetItemClick);
                libraryRecyclerView.setAdapter(adapter);
                break;
        }
    }

    private void debugSheetData() {
        // Get sheets from repository to check if we have data
        SheetMusicRepository repository = new SheetMusicRepository(getApplication());
        repository.getAllSheetMusicWithRelations().observe(this, sheetMusicList -> {
            if (sheetMusicList == null || sheetMusicList.isEmpty()) {
                Log.e("MainActivity", "NO SHEET DATA FOUND IN DATABASE!");

                // Show a toast to notify the developer
                Toast.makeText(this, "No sheet data found. Add some sheets first.", Toast.LENGTH_LONG).show();
            } else {
                Log.d("MainActivity", "Found " + sheetMusicList.size() + " sheets in database");
                Log.d("MainActivity", "First sheet: " + sheetMusicList.get(0).getTitle());

                // Debug the first sheet's metadata
                SheetMusic firstSheet = sheetMusicList.get(0);
                Log.d("MainActivity", "First sheet composers: " + firstSheet.getComposers());
                Log.d("MainActivity", "First sheet genres: " + firstSheet.getGenres());
                Log.d("MainActivity", "First sheet labels: " + firstSheet.getLabels());

                // Check if any sheets have composers, genres, etc.
                boolean hasComposers = false, hasGenres = false, hasLabels = false, hasTags = false;

                for (SheetMusic sheet : sheetMusicList) {
                    if (sheet.getComposers() != null && !sheet.getComposers().isEmpty()) hasComposers = true;
                    if (sheet.getGenres() != null && !sheet.getGenres().isEmpty()) hasGenres = true;
                    if (sheet.getLabels() != null && !sheet.getLabels().isEmpty()) hasLabels = true;
                    if (sheet.getTags() != null && !sheet.getTags().isEmpty()) hasTags = true;
                }

                Log.d("MainActivity", "Sheets with composers: " + hasComposers);
                Log.d("MainActivity", "Sheets with genres: " + hasGenres);
                Log.d("MainActivity", "Sheets with labels: " + hasLabels);
                Log.d("MainActivity", "Sheets with tags: " + hasTags);
            }
        });
    }

    private void clearLibraryButtonsSelection() {
        TextView libraryButton = findViewById(R.id.library_button_text);
        TextView importButton = findViewById(R.id.import_button_text);
        TextView editButton = findViewById(R.id.edit_button_text);

        int defaultColor = getResources().getColor(android.R.color.darker_gray);
        libraryButton.setTextColor(defaultColor);
        importButton.setTextColor(defaultColor);
        editButton.setTextColor(defaultColor);
    }

    private void setupGroupedLibraryView(List<SheetMusic> sheets, Function<SheetMusic, String> groupKeyExtractor, String category) {
        Log.d("MainActivity", "Setting up grouped view for " + category + " with " + sheets.size() + " sheets");

        // Group sheets by the selected attribute
        Map<String, List<SheetMusic>> groupedSheets = new HashMap<>();

        for (SheetMusic sheet : sheets) {
            String groupKey = groupKeyExtractor.apply(sheet);
            if (groupKey == null || groupKey.isEmpty()) {
                groupKey = "Uncategorized";
            }

            Log.d("MainActivity", "Sheet: " + sheet.getTitle() + ", Group key: " + groupKey);

            // Add sheet to appropriate group
            if (!groupedSheets.containsKey(groupKey)) {
                groupedSheets.put(groupKey, new ArrayList<>());
            }
            groupedSheets.get(groupKey).add(sheet);
        }

        // Log group counts
        for (Map.Entry<String, List<SheetMusic>> entry : groupedSheets.entrySet()) {
            Log.d("MainActivity", "Group: " + entry.getKey() + " contains " + entry.getValue().size() + " sheets");
        }

        // Create a list of ONLY group headers
        List<Object> groupedItems = new ArrayList<>();

        // Sort the group keys alphabetically
        List<String> sortedKeys = new ArrayList<>(groupedSheets.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            // Just add the header for group view
            GroupHeader header = new GroupHeader(key, groupedSheets.get(key));
            groupedItems.add(header);
            Log.d("MainActivity", "Added group header: " + key + " with " + header.getSheets().size() + " sheets");
        }

        Log.d("MainActivity", "Creating grouped adapter with " + groupedItems.size() + " header items");

        // Use a grouped adapter for showing groups
        GroupedSheetAdapter groupedAdapter = new GroupedSheetAdapter(this, groupedItems);

        // When a group is clicked, show its sheets directly
        groupedAdapter.setOnGroupClickListener((groupName, groupSheets) -> {
            Log.d("MainActivity", "Group clicked: " + groupName + " with " + groupSheets.size() + " sheets");

            // Lock the sidebar open
            lockSidebarOpen("library");

            // Show direct list of sheets for this group
            showGroupSheets(groupName, groupSheets, getCurrentFilterName());
        });

        // Set the adapter
        if (libraryRecyclerView != null) {
            libraryRecyclerView.setAdapter(groupedAdapter);
            Log.d("MainActivity", "Set grouped adapter on RecyclerView");
        } else {
            Log.e("MainActivity", "libraryRecyclerView is null!");
        }
    }

    private void showSheetListDirectly(String groupName, List<SheetMusic> sheets) {
        Log.d("MainActivity", "Showing sheet list for " + groupName + " with " + sheets.size() + " sheets");

        // Create a direct adapter for the sheets
        SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
        adapter.setOnSheetItemClickListener(this::onSheetItemClick);

        // Set the adapter on the RecyclerView
        if (libraryRecyclerView != null) {
            libraryRecyclerView.setAdapter(adapter);
            Log.d("MainActivity", "Set sheet adapter on RecyclerView");
        } else {
            Log.e("MainActivity", "libraryRecyclerView is null!");
        }
    }
    public void lockSidebarOpen(String sidebarName) {
        // Cancel any ongoing animations
        switch (sidebarName) {
            case "library":
                if (librarySidebar != null) {
                    librarySidebar.animate().cancel();
                    librarySidebar.setTranslationX(0);
                }
                break;
            case "bookmarks":
                if (bookmarksSidebar != null) {
                    bookmarksSidebar.animate().cancel();
                    bookmarksSidebar.setTranslationX(0);
                }
                break;
            case "setlist":
                if (setlistSidebar != null) {
                    setlistSidebar.animate().cancel();
                    setlistSidebar.setTranslationX(0);
                }
                break;
            case "tools":
                if (toolsSidebar != null) {
                    toolsSidebar.animate().cancel();
                    toolsSidebar.setTranslationX(0);
                }
                break;
        }

        // Make sure scrim is visible
        if (scrimOverlay != null) {
            scrimOverlay.setVisibility(View.VISIBLE);
            scrimOverlay.setAlpha(0.5f);
        }

        // Set current sidebar
        currentSidebar = sidebarName;

        // Prevent further animations or state changes
        sidebarLocked = true;
    }

    public String getCurrentFilterName() {
        switch (currentFilter) {
            case COMPOSERS:
                return "Composers";
            case GENRES:
                return "Genres";
            case TAGS:
                return "Tags";
            case LABELS:
                return "Labels";
            default:
                return "Library";
        }
    }

    private void addNavigationHeader(String title) {
        // Get the parent of the RecyclerView (should be library_content_container)
        ViewGroup parent = (ViewGroup) libraryRecyclerView.getParent();

        if (parent == null) {
            Log.e("MainActivity", "Cannot add navigation header: parent view is null");
            return;
        }

        // Check if header already exists and remove it
        View existingHeader = parent.findViewById(R.id.library_navigation_header);
        if (existingHeader != null) {
            parent.removeView(existingHeader);
        }

        // Inflate the navigation header
        View header = getLayoutInflater().inflate(R.layout.library_navigation_header, parent, false);

        if (header == null) {
            Log.e("MainActivity", "Failed to inflate navigation header");
            return;
        }

        // Set the title
        TextView titleView = header.findViewById(R.id.navigation_title);
        if (titleView != null) {
            titleView.setText(title);
        }

        // Set back button click listener
        ImageButton backButton = header.findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                // Unlock the sidebar
                sidebarLocked = false;

                // Go back to the group view
                updateLibraryWithFilter();

                // Remove the header
                removeNavigationHeader();
            });
        }

        // Add at index 0 to put it at the top
        header.setId(R.id.library_navigation_header);
        parent.addView(header, 0);
    }

    private void removeNavigationHeader() {
        Log.d("MainActivity", "Removing navigation header");

        ViewGroup parent = (ViewGroup) libraryRecyclerView.getParent();
        if (parent == null) {
            Log.e("MainActivity", "Cannot remove header: parent view is null");
            return;
        }

        View header = parent.findViewById(R.id.library_navigation_header);
        if (header != null) {
            parent.removeView(header);
            Log.d("MainActivity", "Header removed successfully");
        } else {
            Log.d("MainActivity", "No header found to remove");
        }
    }

    public void showGroupDetailDirect(String groupName, List<SheetMusic> sheets) {
        Log.d("MainActivity", "showGroupDetailDirect: Group=" + groupName + ", Sheets=" + sheets.size());

        // Check if views are available
        if (librarySidebar == null) {
            Log.e("MainActivity", "librarySidebar is null!");
            return; // This return statement is fine
        }

        // Add this line after the return statement above
        librarySidebar.animate().cancel();

        // Force the sidebar to visible position without animation
        librarySidebar.setTranslationX(0);

        // Make sure scrim is visible
        if (scrimOverlay != null) {
            scrimOverlay.setVisibility(View.VISIBLE);
            scrimOverlay.setAlpha(0.5f);
        }

        // Mark sidebar as locked
        sidebarLocked = true;
        currentSidebar = "library";

        // Check RecyclerView
        if (libraryRecyclerView == null) {
            Log.e("MainActivity", "libraryRecyclerView is null!");
            return;
        }

        // Get the layout that contains the RecyclerView
        FrameLayout contentContainer = findViewById(R.id.library_content_container);

        // Check if it's valid
        if (contentContainer != null) {
            // Clear any existing header
            View existingHeader = contentContainer.findViewById(R.id.library_navigation_header);
            if (existingHeader != null) {
                contentContainer.removeView(existingHeader);
            }

            // Add header programmatically
            View header = getLayoutInflater().inflate(R.layout.library_navigation_header, contentContainer, false);
            header.setId(R.id.library_navigation_header);

            // Set title
            TextView titleView = header.findViewById(R.id.navigation_title);
            titleView.setText(getCurrentFilterName() + ": " + groupName);

            // Set back button listener
            ImageButton backButton = header.findViewById(R.id.back_button);
            backButton.setOnClickListener(v -> {
                // Unlock sidebar and return to filter view
                sidebarLocked = false;
                updateLibraryWithFilter();

                // Remove this header
                contentContainer.removeView(header);
            });

            // Add header at the top
            contentContainer.addView(header, 0);

            // Create adapter for sheets
            SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
            adapter.setOnSheetItemClickListener(this::onSheetItemClick);
            if (libraryRecyclerView != null) {
                // Set a bright background color to verify it's visible
                libraryRecyclerView.setBackgroundColor(Color.YELLOW);
                libraryRecyclerView.setAdapter(adapter);
            }

            // Log after setting adapter
            Log.d("MainActivity", "Adapter set with " + sheets.size() + " items");
        } else {
            Log.e("MainActivity", "Content container is null!");
        }
    }

    public void showGroupContents(String groupName, List<SheetMusic> sheets) {

        // 1. First, ensure the sidebar is open
        if (librarySidebar != null) {
            librarySidebar.setTranslationX(0);  // Force position without animation
        }

        // 2. Ensure we're marked as being in sidebar mode
        currentSidebar = "library";
        sidebarLocked = true;

        // 3. Show scrim
        if (scrimOverlay != null) {
            scrimOverlay.setVisibility(View.VISIBLE);
            scrimOverlay.setAlpha(0.5f);
        }

        // 4. Create and show the back navigation header
        FrameLayout contentContainer = findViewById(R.id.library_content_container);

        if (contentContainer != null) {
            // First, remove any existing headers
            View existingHeader = contentContainer.findViewById(R.id.library_navigation_header);
            if (existingHeader != null) {
                contentContainer.removeView(existingHeader);
            }
            contentContainer.setBackgroundColor(Color.CYAN);

            // Inflate a basic header with back button
            LinearLayout header = new LinearLayout(this);
            header.setId(R.id.library_navigation_header);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            header.setPadding(16, 16, 16, 16);
            header.setBackgroundColor(0xFFF0F0F0);  // Light gray background

            // Back button
            ImageButton backButton = new ImageButton(this);
            backButton.setImageResource(R.drawable.round_arrow_back_ios_24);  // Use appropriate icon
            backButton.setBackgroundResource(android.R.color.transparent);
            backButton.setOnClickListener(v -> {
                // Go back to group list
                contentContainer.removeView(header);
                sidebarLocked = false;
                updateLibraryWithFilter();
            });

            // Title text
            TextView titleText = new TextView(this);
            titleText.setText(getCurrentFilterName() + ": " + groupName);
            titleText.setTextSize(16);
            titleText.setPadding(16, 0, 0, 0);

            // Add views to header
            header.addView(backButton);
            header.addView(titleText);

            // Add header to container
            contentContainer.addView(header, 0);
        }
        Log.d("MainActivity", "Showing " + sheets.size() + " sheets for group: " + groupName);
        for (SheetMusic sheet : sheets) {
            Log.d("MainActivity", "Sheet: " + sheet.getTitle());
        }
        // 5. Create adapter to show sheets
        SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
        adapter.setOnSheetItemClickListener(this::onSheetItemClick);
        Log.d("MainActivity", "Adapter item count: " + adapter.getItemCount());

        if (libraryRecyclerView != null) {
            libraryRecyclerView.setAdapter(adapter);
            libraryRecyclerView.setBackgroundColor(Color.YELLOW);
        libraryRecyclerView.post(() -> Log.d("MainActivity", "RecyclerView child count: " + libraryRecyclerView.getChildCount()));
        } else {
            Log.e("MainActivity", "libraryRecyclerView is null!");
        }
    }

    private void showGroupSheets(String groupName, List<SheetMusic> sheets, String category) {
        // Force the sidebar to stay visible
        if (librarySidebar != null) {
            // Cancel any ongoing animations
            librarySidebar.animate().cancel();
            // Ensure the sidebar is at the correct position
            librarySidebar.setTranslationX(0);
        }

        // Make sure the scrim is visible
        if (scrimOverlay != null) {
            scrimOverlay.setVisibility(View.VISIBLE);
            scrimOverlay.setAlpha(0.5f);
        }

        // Set current sidebar and lock it
        currentSidebar = "library";
        sidebarLocked = true;

        // Add a navigation header with back button
        addNavigationHeader(category + ": " + groupName);

        // Create adapter for the sheets
        SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
        adapter.setOnSheetItemClickListener(this::onSheetItemClick);

        // Set the adapter
        if (libraryRecyclerView != null) {
            libraryRecyclerView.setAdapter(adapter);
        }
    }

    private void setupBookmarksMenuButtons() {
        ImageButton addButton = findViewById(R.id.add_bookmark_button);
        ImageButton editButton = findViewById(R.id.edit_bookmarks_button);

        Button pageButton = findViewById(R.id.page_button);
        Button titleButton = findViewById(R.id.title_button);
        Button flagsButton = findViewById(R.id.flags_button);
        Button tocButton = findViewById(R.id.toc_button);

        addButton.setOnClickListener(v -> {
            // Add a new bookmark
        });

        editButton.setOnClickListener(v -> {
            // Enable edit mode for bookmarks
        });

        // Set up filter buttons
        pageButton.setOnClickListener(v -> {
            // Filter bookmarks by page
        });

        titleButton.setOnClickListener(v -> {
            // Filter bookmarks by title
        });

        flagsButton.setOnClickListener(v -> {
            // Filter bookmarks by flags
        });

        tocButton.setOnClickListener(v -> {
            // Show table of contents
        });
    }

    private void setupSetlistMenuButtons() {
        ImageButton addButton = findViewById(R.id.add_setlist_button);
        ImageButton editButton = findViewById(R.id.edit_setlist_button);

        Button manualButton = findViewById(R.id.manual_button);
        Button titleButton = findViewById(R.id.setlist_title_button);
        Button freshButton = findViewById(R.id.fresh_button);

        addButton.setOnClickListener(v -> {
            // Add a new setlist
        });

        editButton.setOnClickListener(v -> {
            // Enable edit mode for setlists
        });

        // Set up filter buttons
        manualButton.setOnClickListener(v -> {
            // Show manual ordering
        });

        titleButton.setOnClickListener(v -> {
            // Sort by title
        });

        freshButton.setOnClickListener(v -> {
            // Show most recently used
        });
    }

    private boolean isInGroupDetailView() {
        return findViewById(R.id.library_navigation_header) != null;
    }

    public void showGroupDetail(String groupName, List<SheetMusic> sheets) {
        // Create fragment
        GroupDetailFragment fragment = GroupDetailFragment.newInstance(
                groupName, sheets, getCurrentFilterName());

        // Replace current library content with fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.library_content_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    public void showGroupList() {
        // Pop back stack to return to group list
        getSupportFragmentManager().popBackStack();

        // Make sure filter is still applied
        updateLibraryWithFilter();
    }
    private void setupListeners() {
        // Set up scrim overlay click to dismiss sidebars
        scrimOverlay.setOnClickListener(v -> {
            if (isInGroupDetailView()) {
                // In group detail view - don't close sidebar
                Log.d("MainActivity", "In group detail view - keeping sidebar open");
            } else if (sidebarLocked) {
                // Sidebar is locked - don't close
                Log.d("MainActivity", "Sidebar locked - keeping open");
            } else {
                // Normal case - close sidebar
                closeSidebars();
            }
        });
        // Set up toolbar buttons
        libraryButton.setOnClickListener(v -> toggleSidebar("library"));
        bookmarksButton.setOnClickListener(v -> toggleSidebar("bookmarks"));
        setlistButton.setOnClickListener(v -> toggleSidebar("setlist"));
        toolsButton.setOnClickListener(v -> toggleSidebar("tools"));

        metronomeButton.setOnClickListener(v -> {
            // Show metronome dialog or control
        });

        searchButton.setOnClickListener(v -> toggleSearchDialog());

        // Set up resize handles
        setupResizeHandles();

        // Set up content container to dismiss sidebars and toggle toolbar
        View contentContainer = findViewById(R.id.content_container);

        contentContainer.setOnClickListener(v -> {
            if (currentSidebar != null) {
                closeSidebars();
            } else {
                toggleToolbarVisibility();
            }
        });
    }

    private void setupResizeHandles() {
        // Setup resize handlers for each sidebar
        SidebarResizeHandler libraryHandler = new SidebarResizeHandler(librarySidebar, 250, 500, true);
        libraryResizeHandle.setOnTouchListener(libraryHandler);

        SidebarResizeHandler bookmarksHandler = new SidebarResizeHandler(bookmarksSidebar, 250, 500, true);
        bookmarksResizeHandle.setOnTouchListener(bookmarksHandler);

        SidebarResizeHandler setlistHandler = new SidebarResizeHandler(setlistSidebar, 250, 500, true);
        setlistResizeHandle.setOnTouchListener(setlistHandler);

        SidebarResizeHandler toolsHandler = new SidebarResizeHandler(toolsSidebar, 250, 500, false);
        toolsResizeHandle.setOnTouchListener(toolsHandler);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupRecyclerViews() {
//        View libraryContentContainer = findViewById(R.id.library_content_container);
//        if (libraryContentContainer instanceof ViewGroup) {
//            ViewGroup container = (ViewGroup) libraryContentContainer;
//
//            // Set a fixed height for testing
//            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
//            if (containerParams != null) {
//                containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
//                container.setLayoutParams(containerParams);
//            } else {
//                Log.e("MainActivity", "Container params are null");
//            }
//
//            // Make sure the RecyclerView fills its parent
//            ViewGroup.LayoutParams rvParams = libraryRecyclerView.getLayoutParams();
//            if (rvParams != null) {
//                rvParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
//                libraryRecyclerView.setLayoutParams(rvParams);
//            } else {
//                Log.e("MainActivity", "RecyclerView params are null");
//            }
//
//            // Add explicit height
//            libraryRecyclerView.setMinimumHeight(1000);
//
//            // Force layout
//            container.requestLayout();
//            libraryRecyclerView.requestLayout();
//
//            Log.d("MainActivity", "Fixed layout params for better visibility");
//        }
        // Set up library recycler view
        libraryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        SheetMusicAdapter libraryAdapter = new SheetMusicAdapter(this, new ArrayList<>());
        libraryAdapter.setOnSheetItemClickListener(this::onSheetItemClick);
        libraryRecyclerView.setAdapter(libraryAdapter);

        // Observe sheet data changes and update the adapter when data changes
        SheetMusicRepository repository = new SheetMusicRepository(getApplication());
        repository.getAllSheetMusicWithRelations().observe(this, sheetMusicList -> {
            Log.d("MainActivity", "SheetMusic list returned: " +
                    (sheetMusicList == null ? "null" : sheetMusicList.size() + " items"));

            if (sheetMusicList != null && !sheetMusicList.isEmpty()) {
                Log.d("MainActivity", "First sheet: " + sheetMusicList.get(0).getTitle());

                // Only update the adapter if we're in the default view (no filters)
                if (currentFilter == FilterType.NONE) {
                    libraryAdapter.updateSheets(sheetMusicList);
                } else {
                    // If filter is active, apply it to the new data
                    applyFilterToSheets(sheetMusicList);
                }
            } else {
                Log.d("MainActivity", "No sheets to display");
                libraryAdapter.updateSheets(new ArrayList<>());
            }
        });

        // Set up bookmarks recycler view
        bookmarksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        List<BookmarkItem> bookmarkItems = getBookmarkItems();
        BookmarkAdapter bookmarkAdapter = new BookmarkAdapter(bookmarkItems);
        bookmarksRecyclerView.setAdapter(bookmarkAdapter);

        toolsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        List<ToolItem> toolItems = getToolItems();
        ToolAdapter toolAdapter = new ToolAdapter(toolItems);
        toolAdapter.setSheetViewModel(sheetViewModel, this);

        toolsRecyclerView.setAdapter(toolAdapter);

        // Ensure RecyclerView touch events are handled properly
        libraryRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // Don't intercept touch events - let them be processed by the RecyclerView
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // Not used
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // Not used
            }
        });
    }

    // Sample data methods
    private List<SheetEntity> getSheetMusicItems() {
        return sheetViewModel.getAllSheets().getValue();
    }

    public void onSheetItemClick(SheetMusic sheet) {
        // Log that we're handling a click
        Log.d("MainActivity", "onSheetItemClick called for: " + sheet.getTitle() + " (ID: " + sheet.getId() + ")");

        // Close the sidebar if it's not locked
        if (currentSidebar != null && !sidebarLocked) {
            closeSidebars();
        }

        // ⭐ CRITICAL FIX: Select the sheet in the ViewModel
        // This line was MISSING - add it here!
        sheetViewModel.selectSheet(sheet.getId());
        Log.d("MainActivity", "✓ Sheet selected in ViewModel with ID: " + sheet.getId());

        // Create the detail fragment with the selected sheet ID
        SheetDetailFragment detailFragment = SheetDetailFragment.newInstance(sheet.getId());

        // Replace current fragment with the detail fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_container, detailFragment)
                .addToBackStack(null) // This allows back button to return to previous state
                .commit();

        // Update toolbar title
        updateToolbarTitle(sheet.getTitle());
    }

    public void updateToolbarTitle(String title) {
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        if (toolbarTitle != null) {
            toolbarTitle.setText(title);
        }
    }

    private List<BookmarkItem> getBookmarkItems() {
        List<BookmarkItem> items = new ArrayList<>();
        items.add(new BookmarkItem("Introduction", 1));
        items.add(new BookmarkItem("First Movement", 2));
        items.add(new BookmarkItem("Second Movement", 5));
        items.add(new BookmarkItem("Coda", 8));
        return items;
    }


// Step 1: Add Crop tool to MainActivity.getToolItems() method:

    private List<ToolItem> getToolItems() {
        List<ToolItem> items = new ArrayList<>();
        items.add(new ToolItem("Annotate", R.drawable.round_edit_note_24));
        items.add(new ToolItem("Rearrange", R.drawable.round_edit_note_24));
        items.add(new ToolItem("Crop", R.drawable.ic_crop)); // Add crop tool
        items.add(new ToolItem("Backup", R.drawable.round_home_24));
        items.add(new ToolItem("Restore", R.drawable.round_home_24));
        items.add(new ToolItem("Settings", R.drawable.ic_info));
        return items;
    }

    private void toggleSidebar(String sidebarName) {
        Log.d("MainActivity", "Toggling sidebar: " + sidebarName + ", Current: " + currentSidebar + ", Locked: " + sidebarLocked);

        if (sidebarLocked) {
            // Only allow the same sidebar to be "toggled" which will unlock it
            if (currentSidebar != null && currentSidebar.equals(sidebarName)) {
                Log.d("MainActivity", "Unlocking sidebar: " + sidebarName);
                sidebarLocked = false;
                closeSidebars();
            }
            return;
        }

        if (currentSidebar != null) {
            // A sidebar is already open
            if (currentSidebar.equals(sidebarName)) {
                // Same sidebar - close it
                Log.d("MainActivity", "Closing sidebar: " + sidebarName);
                closeSidebars();
                return;
            } else {
                // Different sidebar - close current one first
                Log.d("MainActivity", "Switching from " + currentSidebar + " to " + sidebarName);
                closeSidebars();
            }
        }

        // Show the specific sidebar that was clicked with animation
        switch (sidebarName) {
            case "library":
                Log.d("MainActivity", "Opening library sidebar");
                if (librarySidebar != null) {
                    // Make sure it's visible before animating
                    librarySidebar.setVisibility(View.VISIBLE);
                    librarySidebar.animate()
                            .translationX(0)
                            .setDuration(250)
                            .start();
                } else {
                    Log.e("MainActivity", "Library sidebar is null!");
                }
                break;
            case "bookmarks":
                Log.d("MainActivity", "Opening bookmarks sidebar");
                if (bookmarksSidebar != null) {
                    bookmarksSidebar.setVisibility(View.VISIBLE);
                    bookmarksSidebar.animate()
                            .translationX(0)
                            .setDuration(250)
                            .start();
                } else {
                    Log.e("MainActivity", "Bookmarks sidebar is null!");
                }
                break;
            case "setlist":
                Log.d("MainActivity", "Opening setlist sidebar");
                if (setlistSidebar != null) {
                    setlistSidebar.setVisibility(View.VISIBLE);
                    setlistSidebar.animate()
                            .translationX(0)
                            .setDuration(250)
                            .start();
                } else {
                    Log.e("MainActivity", "Setlist sidebar is null!");
                }
                break;
            case "tools":
                Log.d("MainActivity", "Opening tools sidebar");
                if (toolsSidebar != null) {
                    toolsSidebar.setVisibility(View.VISIBLE);
                    toolsSidebar.animate()
                            .translationX(0)
                            .setDuration(250)
                            .start();
                } else {
                    Log.e("MainActivity", "Tools sidebar is null!");
                }
                break;
        }

        // Show the scrim overlay with fade-in animation
        if (scrimOverlay != null) {
            scrimOverlay.setAlpha(0);
            scrimOverlay.setVisibility(View.VISIBLE);
            scrimOverlay.animate()
                    .alpha(0.5f)
                    .setDuration(250)
                    .start();
        } else {
            Log.e("MainActivity", "Scrim overlay is null!");
        }

        currentSidebar = sidebarName;
    }

    public void setCurrentSidebar(String sidebar) {
        this.currentSidebar = sidebar;
    }

    public void transitionToGroupDetail(String groupName, List<SheetMusic> sheets) {
        // This is a direct state transition with no animations
        Log.d("MainActivity", "Transitioning to group: " + groupName);

        // First ensure sidebar is locked open
        lockSidebarOpen("library");

        // Add navigation header
        removeNavigationHeader();
        addNavigationHeader(getCurrentFilterName() + ": " + groupName);

        // Create and set adapter
        SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheets);
        adapter.setOnSheetItemClickListener(this::onSheetItemClick);
        libraryRecyclerView.setAdapter(adapter);
    }


    private void closeSidebars() {
        Log.d("MainActivity", "Closing sidebars, current: " + currentSidebar + ", locked: " + sidebarLocked);

        if (sidebarLocked) {
            Log.d("MainActivity", "Sidebar is locked, not closing");
            return;
        }

        // Animate all sidebars to their hidden positions
        if (librarySidebar != null) {
            librarySidebar.animate()
                    .translationX(-librarySidebar.getWidth())
                    .setDuration(250)
                    .withEndAction(() -> {
                        // Reset any navigation headers or filter states if needed
                        if (currentSidebar != null && currentSidebar.equals("library")) {
                            removeNavigationHeader();
                        }
                    })
                    .start();
        } else {
            Log.e("MainActivity", "Library sidebar is null!");
        }

        if (bookmarksSidebar != null) {
            bookmarksSidebar.animate()
                    .translationX(-bookmarksSidebar.getWidth())
                    .setDuration(250)
                    .start();
        } else {
            Log.e("MainActivity", "Bookmarks sidebar is null!");
        }

        if (setlistSidebar != null) {
            setlistSidebar.animate()
                    .translationX(-setlistSidebar.getWidth())
                    .setDuration(250)
                    .start();
        } else {
            Log.e("MainActivity", "Setlist sidebar is null!");
        }

        if (toolsSidebar != null) {
            toolsSidebar.animate()
                    .translationX(toolsSidebar.getWidth())
                    .setDuration(250)
                    .start();
        } else {
            Log.e("MainActivity", "Tools sidebar is null!");
        }

        // Fade out the scrim overlay
        if (scrimOverlay != null) {
            scrimOverlay.animate()
                    .alpha(0)
                    .setDuration(250)
                    .withEndAction(() -> scrimOverlay.setVisibility(View.GONE))
                    .start();
        } else {
            Log.e("MainActivity", "Scrim overlay is null!");
        }

        currentSidebar = null;
    }

    private void toggleSearchDialog() {
        if (searchDialog.getVisibility() == View.VISIBLE) {
            searchDialog.setVisibility(View.GONE);
        } else {
            searchDialog.setVisibility(View.VISIBLE);

            Button cancelButton = findViewById(R.id.cancel_search_button);
            Button searchButton = findViewById(R.id.submit_search_button);

            cancelButton.setOnClickListener(v -> searchDialog.setVisibility(View.GONE));
            searchButton.setOnClickListener(v -> {
                // Perform search
                searchDialog.setVisibility(View.GONE);
            });
        }
    }

    private void toggleToolbarVisibility() {
        View toolbar = findViewById(R.id.app_toolbar);

        if (isToolbarVisible) {
            toolbar.animate().translationY(-toolbar.getHeight()).setDuration(200);
        } else {
            toolbar.animate().translationY(0).setDuration(200);
        }

        isToolbarVisible = !isToolbarVisible;
    }


    private void showAddSheetDialog() {
        // Create file picker intent
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        // Launch file picker using the new API
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.getData() != null) {
                        Uri fileUri = data.getData();
                        String fileName = getFileNameFromUri(fileUri);

                        // Determine if it's a PDF or image
                        boolean isPdf = false;
                        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                            isPdf = true;
                        }

                        if (!isPdf) {
                            // For non-PDFs, prompt for a name
                            showNameInputDialog(fileUri, fileName);
                        } else {
                            // For PDFs, add directly
                            processAddSheet(fileUri, null);
                        }
                    }
                }
            }
    );

    private void showNameInputDialog(Uri fileUri, String originalFileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Sheet Name");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (originalFileName != null) {
            input.setText(originalFileName.contains(".") ?
                    originalFileName.substring(0, originalFileName.lastIndexOf('.')) :
                    originalFileName);
        }
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String sheetName = input.getText().toString().trim();
            if (!sheetName.isEmpty()) {
                processAddSheet(fileUri, sheetName);
            } else {
                Toast.makeText(MainActivity.this, "Sheet name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void processAddSheet(Uri fileUri, String suggestedName) {
        // Show loading indicator
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Adding sheet...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        sheetViewModel.addSheet(fileUri, suggestedName, (sheetId, errorMessage) -> {
            // Hide loading indicator
            progressDialog.dismiss();

            if (sheetId != -1) {
                Toast.makeText(MainActivity.this, "Sheet added successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, errorMessage != null ? errorMessage : "Error adding sheet", Toast.LENGTH_LONG).show();
            }
        });
    }
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    /**
     * Get file extension from name
     */
    private String getFileExtension(String fileName) {
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        } else {
            return "";
        }
    }

    /**
     * Remove extension from file name
     */
    private String removeExtension(String fileName) {
        if (fileName.lastIndexOf(".") != -1) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    // Add this method to your MainActivity
    @SuppressLint("SetTextI18n")
    private void showLibrarySelectionDialog() {
        // Create a dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Library");

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_library_selection, null);
        builder.setView(dialogView);

        // Get the RecyclerView from the layout
        RecyclerView libraryListView = dialogView.findViewById(R.id.library_selection_recycler_view);
        libraryListView.setLayoutManager(new LinearLayoutManager(this));

        // Create "All Sheets" option at the top
        View allSheetsHeader = dialogView.findViewById(R.id.all_sheets_option);
        TextView allSheetsText = allSheetsHeader.findViewById(R.id.all_sheets_text);
        TextView all_sheets_count = allSheetsHeader.findViewById(R.id.all_sheets_count);
        allSheetsText.setText("All Sheets");
        all_sheets_count.setText(sheetViewModel.getAllSheets() + " sheets");
        // Set click listener for "All Sheets" option
        allSheetsHeader.setOnClickListener(v -> {
            // Load all sheets
            loadAllSheets();
            // Update toolbar title
            updateToolbarTitle("All Sheets");
            // Dismiss dialog
            builder.create().dismiss();
        });

        // Add Create New Library button
        Button createNewButton = dialogView.findViewById(R.id.create_new_library_button);
        createNewButton.setOnClickListener(v -> {
            showCreateNewLibraryDialog();
            builder.create().dismiss();
        });

        // Create adapter for library list
        LibraryRepository libraryRepository = new LibraryRepository(getApplication());

        // Observe libraries and set up adapter when data is ready
        libraryRepository.getAllLibrariesWithSheets().observe(this, libraries -> {
            LibrarySelectionAdapter adapter = new LibrarySelectionAdapter(libraries, library -> {
                // Handle library selection
                loadLibrary(library);
                updateToolbarTitle(library.getName());
                builder.create().dismiss();
            });
            libraryListView.setAdapter(adapter);
        });

        // Show the dialog
        builder.create().show();
    }

    // Method to load all sheets
    private void loadAllSheets() {
        currentLibrary = null;
        currentFilter = FilterType.NONE;
        clearFilterButtons();

        SheetMusicRepository repository = new SheetMusicRepository(getApplication());
        repository.getAllSheetMusicWithRelations().observe(this, sheetMusicList -> {
            // Check the adapter type before updating
            RecyclerView.Adapter<?> currentAdapter = libraryRecyclerView.getAdapter();

            if (currentAdapter instanceof GroupedSheetAdapter) {
                // Create a new SheetMusicAdapter
                SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheetMusicList);
                adapter.setOnSheetItemClickListener(this::onSheetItemClick);
                libraryRecyclerView.setAdapter(adapter);
            } else if (currentAdapter instanceof SheetMusicAdapter) {
                // Update existing adapter
                SheetMusicAdapter adapter = (SheetMusicAdapter) currentAdapter;
                adapter.updateSheets(sheetMusicList);
            } else {
                // No adapter or unknown type, create new one
                SheetMusicAdapter adapter = new SheetMusicAdapter(this, sheetMusicList);
                adapter.setOnSheetItemClickListener(this::onSheetItemClick);
                libraryRecyclerView.setAdapter(adapter);
            }

            // Open library sidebar if it's not already open
            toggleSidebar("library");
        });
    }
    // Method to load a specific library
    private void loadLibrary(Library library) {
        currentLibrary = library;
        currentFilter = FilterType.NONE;
        clearFilterButtons();

        // Check the adapter type before updating
        RecyclerView.Adapter<?> currentAdapter = libraryRecyclerView.getAdapter();

        if (currentAdapter instanceof GroupedSheetAdapter) {
            // Create a new SheetMusicAdapter
            SheetMusicAdapter adapter = new SheetMusicAdapter(this, library.getSheets());
            adapter.setOnSheetItemClickListener(this::onSheetItemClick);
            libraryRecyclerView.setAdapter(adapter);
        } else if (currentAdapter instanceof SheetMusicAdapter) {
            // Update existing adapter
            SheetMusicAdapter adapter = (SheetMusicAdapter) currentAdapter;
            adapter.updateSheets(library.getSheets());
        } else {
            // No adapter or unknown type, create new one
            SheetMusicAdapter adapter = new SheetMusicAdapter(this, library.getSheets());
            adapter.setOnSheetItemClickListener(this::onSheetItemClick);
            libraryRecyclerView.setAdapter(adapter);
        }

        // Open library sidebar if it's not already open
        toggleSidebar("library");
    }
    // Method to show dialog for creating a new library
    private void showCreateNewLibraryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Library");

        // Set up the input fields
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_library, null);
        final EditText nameInput = dialogView.findViewById(R.id.library_name_input);
        final EditText descriptionInput = dialogView.findViewById(R.id.library_description_input);

        builder.setView(dialogView);

        // Set up the buttons
        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();

            if (!name.isEmpty()) {
                // Create new library entity
                LibraryEntity newLibrary = new LibraryEntity(name, description);

                // Save to database via repository
                LibraryRepository repository = new LibraryRepository(getApplication());
                repository.insertLibrary(newLibrary);

                Toast.makeText(MainActivity.this, "Library created", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Library name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }


    /**
     * Add dummy data for testing purposes.
     * This method can be called to populate the database with sample sheets.
     */
    private void addDummyData() {
        Log.d("MainActivity", "Adding dummy data for testing");

        // Get the SheetViewModel instead of repository directly
        SheetViewModel viewModel = new SheetViewModel(getApplication());

        // Check if we already have sheets
        viewModel.getAllSheets().observe(this, existingSheets -> {
            if (existingSheets == null || existingSheets.isEmpty()) {
                Log.d("MainActivity", "No existing sheets found, adding dummy data");

                // Create dummy sheet entities
                List<SheetEntity> dummySheets = new ArrayList<>();

                // Sheet 1
                SheetEntity sheet1 = new SheetEntity(
                        "Moonlight Sonata",
                        "moonlight_sonata.pdf",
                        "/path/to/moonlight_sonata.pdf",
                        1024 * 1024, // 1MB
                        10 // 10 pages
                );
                sheet1.setComposers("Ludwig van Beethoven");
                sheet1.setGenres("Classical");
                sheet1.setLabels("Piano");
                sheet1.setReferences("Op. 27, No. 2");
                sheet1.setRating(5.0f);
                sheet1.setKey("C# Minor");
                dummySheets.add(sheet1);

                // Sheet 2
                SheetEntity sheet2 = new SheetEntity(
                        "Für Elise",
                        "fur_elise.pdf",
                        "/path/to/fur_elise.pdf",
                        512 * 1024, // 512KB
                        5 // 5 pages
                );
                sheet2.setComposers("Ludwig van Beethoven");
                sheet2.setGenres("Classical");
                sheet2.setLabels("Piano");
                sheet2.setReferences("WoO 59");
                sheet2.setRating(4.5f);
                sheet2.setKey("A Minor");
                dummySheets.add(sheet2);

                // Sheet 3
                SheetEntity sheet3 = new SheetEntity(
                        "Nocturne in Eb Major",
                        "nocturne_op9_no2.pdf",
                        "/path/to/nocturne_op9_no2.pdf",
                        768 * 1024, // 768KB
                        7 // 7 pages
                );
                sheet3.setComposers("Frédéric Chopin");
                sheet3.setGenres("Romantic");
                sheet3.setLabels("Piano");
                sheet3.setReferences("Op. 9, No. 2");
                sheet3.setRating(4.8f);
                sheet3.setKey("Eb Major");
                dummySheets.add(sheet3);

                // Sheet 4
                SheetEntity sheet4 = new SheetEntity(
                        "The Four Seasons - Spring",
                        "four_seasons_spring.pdf",
                        "/path/to/four_seasons_spring.pdf",
                        1536 * 1024, // 1.5MB
                        15 // 15 pages
                );
                sheet4.setComposers("Antonio Vivaldi");
                sheet4.setGenres("Baroque");
                sheet4.setLabels("Violin, Orchestra");
                sheet4.setReferences("Op. 8, RV 269");
                sheet4.setRating(4.7f);
                sheet4.setKey("E Major");
                dummySheets.add(sheet4);

                // Sheet 5
                SheetEntity sheet5 = new SheetEntity(
                        "Clair de Lune",
                        "clair_de_lune.pdf",
                        "/path/to/clair_de_lune.pdf",
                        896 * 1024, // 896KB
                        8 // 8 pages
                );
                sheet5.setComposers("Claude Debussy");
                sheet5.setGenres("Impressionist");
                sheet5.setLabels("Piano");
                sheet5.setReferences("Suite Bergamasque");
                sheet5.setRating(4.9f);
                sheet5.setKey("Db Major");
                dummySheets.add(sheet5);

                // Insert all sheets using the appropriate methods
                new Thread(() -> {
                    for (SheetEntity sheet : dummySheets) {
                        // Use insertSheet instead of insertSheetSync
                        viewModel.insertSheet(sheet);
                    }

                    // Log completion
                    Log.d("MainActivity", "Dummy data insertion complete");

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Sample data added", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            } else {
                Log.d("MainActivity", "Found " + existingSheets.size() + " existing sheets, not adding dummy data");
            }
        });
    }
}