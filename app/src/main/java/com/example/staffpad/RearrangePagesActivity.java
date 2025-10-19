package com.example.staffpad;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staffpad.adapters.RearrangePagesAdapter;
import com.example.staffpad.database.AppDatabase;
import com.example.staffpad.database.SheetDao;
import com.example.staffpad.database.SheetEntity;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RearrangePagesActivity extends AppCompatActivity implements RearrangePagesAdapter.Listener {

    public static final String EXTRA_SHEET_ID = "extra_sheet_id";
    public static final String RESULT_CHANGED = "result_changed";
    private long sheetId;
    private SheetEntity sheet;
    private RecyclerView recyclerView;
    private RearrangePagesAdapter adapter;
    private int originalTotalCount = 0;
    private MenuItem deleteItem;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rearrange_pages);

        sheetId = getIntent().getLongExtra(EXTRA_SHEET_ID, -1);
        if (sheetId <= 0) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_select) {
                boolean enable = !adapter.isSelectionMode();
                adapter.setSelectionMode(enable);
                adapter.setMultiSelectEnabled(false);
                return true;
            } else if (id == R.id.action_multi_select) {
                boolean enable = !(adapter.isSelectionMode() && adapter.isMultiSelectEnabled());
                adapter.setSelectionMode(enable);
                adapter.setMultiSelectEnabled(enable);
                return true;
            } else if (id == R.id.action_delete) {
                adapter.deleteSelected();
                return true;
            } else if (id == R.id.action_save) {
                onSave();
                return true;
            }
            return false;
        });

        deleteItem = toolbar.getMenu().findItem(R.id.action_delete);

        recyclerView = findViewById(R.id.recyclerViewPages);
        int span = 3; // show 3 items per row
        recyclerView.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new RearrangePagesAdapter(this);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                int from = vh.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override
            public boolean isLongPressDragEnabled() { return false; }
        });
        helper.attachToRecyclerView(recyclerView);
        // Allow adapter to request startDrag on touch-down when not in selection mode
        adapter.setDragHelper(helper);

        loadData();
    }

    private void loadData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SheetDao dao = AppDatabase.getDatabase(getApplicationContext()).sheetDao();
                sheet = dao.getSheetByIdSync(sheetId);
                if (sheet == null) { finish(); return; }
                File file = new File(sheet.getFilePath());
                List<RearrangePagesAdapter.PageItem> list = new ArrayList<>();
                originalTotalCount = getOriginalPageCount(file);

                // Build current mapping from JSON if exists, else identity
                List<Integer> logicalToOriginal = new ArrayList<>();
                try {
                    if (sheet.getPageOrderJson() != null && !sheet.getPageOrderJson().isEmpty()) {
                        JSONArray arr = new JSONArray(sheet.getPageOrderJson());
                        for (int i = 0; i < arr.length(); i++) logicalToOriginal.add(arr.getInt(i));
                    }
                } catch (Throwable ignored) {}
                // apply deletions filtering
                java.util.HashSet<Integer> deleted = new java.util.HashSet<>();
                try {
                    if (sheet.getDeletedPagesJson() != null && !sheet.getDeletedPagesJson().isEmpty()) {
                        JSONArray del = new JSONArray(sheet.getDeletedPagesJson());
                        for (int i = 0; i < del.length(); i++) deleted.add(del.getInt(i));
                    }
                } catch (Throwable ignored) {}

                if (logicalToOriginal.isEmpty()) {
                    for (int i = 0; i < originalTotalCount; i++) if (!deleted.contains(i)) logicalToOriginal.add(i);
                } else {
                    // filter by deleted
                    List<Integer> filtered = new ArrayList<>();
                    for (int v : logicalToOriginal) if (!deleted.contains(v)) filtered.add(v);
                    logicalToOriginal = filtered;
                }

                // Render thumbnails small
                for (int origIndex : logicalToOriginal) {
                    Bitmap thumb = renderThumbnail(file, origIndex, 0.2f);
                    list.add(new RearrangePagesAdapter.PageItem(origIndex, thumb));
                }

                runOnUiThread(() -> {
                    adapter.setItems(list);
                });
            } catch (Exception e) {
                Log.e("Rearrange", "loadData failed", e);
                runOnUiThread(this::finish);
            }
        });
    }

    private int getOriginalPageCount(File file) {
        ParcelFileDescriptor fd = null; PdfRenderer pr = null;
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pr = new PdfRenderer(fd);
            return pr.getPageCount();
        } catch (Exception e) {
            return 0;
        } finally {
            try { if (pr != null) pr.close(); } catch (Throwable ignore) {}
            try { if (fd != null) fd.close(); } catch (Throwable ignore) {}
        }
    }

    private Bitmap renderThumbnail(File file, int pageIndex, float scale) {
        ParcelFileDescriptor fd = null; PdfRenderer pr = null; PdfRenderer.Page page = null;
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pr = new PdfRenderer(fd);
            if (pageIndex < 0 || pageIndex >= pr.getPageCount()) return null;
            page = pr.openPage(pageIndex);
            int w = Math.max(1, (int)(page.getWidth() * scale));
            int h = Math.max(1, (int)(page.getHeight() * scale));
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (page != null) page.close(); } catch (Throwable ignore) {}
            try { if (pr != null) pr.close(); } catch (Throwable ignore) {}
            try { if (fd != null) fd.close(); } catch (Throwable ignore) {}
        }
    }

    private void onSave() {
        List<Integer> order = adapter.computeOrderMapping();
        List<Integer> deleted = adapter.computeDeletedOriginals(originalTotalCount);
        JSONArray orderJson = new JSONArray();
        for (int v : order) orderJson.put(v);
        JSONArray delJson = new JSONArray();
        for (int v : deleted) delJson.put(v);
        String orderStr = orderJson.toString();
        String delStr = delJson.toString();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SheetDao dao = AppDatabase.getDatabase(getApplicationContext()).sheetDao();
                SheetEntity s = dao.getSheetByIdSync(sheetId);
                if (s != null) {
                    s.setPageOrderJson(orderStr);
                    s.setDeletedPagesJson(delStr);
                    dao.updateSheet(s);
                }
                runOnUiThread(() -> {
                    Intent result = new Intent();
                    result.putExtra(RESULT_CHANGED, true);
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setResult(RESULT_CANCELED); finish(); });
            }
        });
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (deleteItem != null) {
            deleteItem.setVisible(selectedCount > 0);
        }
    }
}
