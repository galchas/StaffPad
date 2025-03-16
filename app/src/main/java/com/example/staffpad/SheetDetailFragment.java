package com.example.staffpad;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.staffpad.database.SheetEntity;
import com.example.staffpad.viewmodel.SheetViewModel;
import com.github.chrisbanes.photoview.PhotoView;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.io.IOException;

public class SheetDetailFragment extends Fragment {

    private static final String ARG_SHEET_ID = "sheet_id";
    private long sheetId = -1;
    private SheetViewModel sheetViewModel;
    private PhotoView photoView;

    public SheetDetailFragment() {
        // Required empty public constructor
    }

    public static SheetDetailFragment newInstance(long sheetId) {
        SheetDetailFragment fragment = new SheetDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SHEET_ID, sheetId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sheetId = getArguments().getLong(ARG_SHEET_ID);
        }

        // Initialize ViewModel
        sheetViewModel = new ViewModelProvider(requireActivity()).get(SheetViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sheet_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        photoView = view.findViewById(R.id.photo_view);

        if (sheetId != -1) {
            // Get the sheet using LiveData for proper lifecycle management
            sheetViewModel.getSheetById(sheetId).observe(getViewLifecycleOwner(), this::displaySheet);
        }
    }
    private void displaySheet(SheetEntity sheet) {
        if (sheet == null) {
            Log.e("SheetDetail", "Sheet not found for id: " + sheetId);
            return;
        }

        // Log that we're displaying a sheet
        Log.d("SheetDetail", "Displaying sheet: " + sheet.getTitle() + " from file: " + sheet.getFilePath());

        // Update toolbar title
        try {
            ((MainActivity) requireActivity()).updateToolbarTitle(sheet.getTitle());
        } catch (Exception e) {
            Log.e("SheetDetail", "Could not update toolbar title", e);
        }

        // Load the PDF and convert to bitmap
        try {
            File pdfFile = new File(sheet.getFilePath());

            // Check if file exists
            if (!pdfFile.exists()) {
                Log.e("SheetDetail", "PDF file does not exist: " + pdfFile.getAbsolutePath());

                // Show an error message in the PhotoView
                Bitmap errorBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(errorBitmap);
                canvas.drawColor(Color.WHITE);

                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setTextSize(36);
                paint.setTextAlign(Paint.Align.CENTER);

                canvas.drawText("Error: PDF file not found", 400, 300, paint);
                photoView.setImageBitmap(errorBitmap);

                return;
            }

            PDDocument document = PDDocument.load(pdfFile);
            PDFRenderer renderer = new PDFRenderer(document);

            // Render first page for now
            Bitmap bitmap = renderer.renderImage(0);
            photoView.setImageBitmap(bitmap);

            // Enable zooming
            photoView.setMaximumScale(5.0f);

            Log.d("SheetDetail", "PDF loaded successfully: " + sheet.getFilePath() +
                    ", pages: " + document.getNumberOfPages());

            document.close();
        } catch (IOException e) {
            Log.e("SheetDetail", "Error loading PDF", e);

            // Show error message in the PhotoView
            Bitmap errorBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(errorBitmap);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setTextSize(36);
            paint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText("Error loading PDF: " + e.getMessage(), 400, 300, paint);
            photoView.setImageBitmap(errorBitmap);
        }
    }
}