package com.example.staffpad.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                SheetEntity.class,
                SetlistEntity.class,
                AudioEntity.class,
                TagEntity.class,
                SheetSetlistCrossRef.class,
                SheetLibraryCrossRef.class,
                SheetTagCrossRef.class,
                LibraryEntity.class,
                PageLayerEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SheetDao sheetDao();
    public abstract SetlistDao setlistDao();
    public abstract AudioDao audioDao();
    public abstract TagDao tagDao();
    public abstract LibraryDao libraryDao();
    public abstract PageLayerDao pageLayerDao();
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "staffpad_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Override the onCreate method to populate the database with initial data.
     */
    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            // Populate the database in the background
            databaseWriteExecutor.execute(() -> {
                // Populate with sample data if you want
                SheetDao sheetDao = INSTANCE.sheetDao();
                SetlistDao setlistDao = INSTANCE.setlistDao();
                TagDao tagDao = INSTANCE.tagDao();

                // Insert sample setlists
                SetlistEntity setlist1 = new SetlistEntity("Classical Favorites", "My favorite classical pieces");
                SetlistEntity setlist2 = new SetlistEntity("Practice Routine", "Daily practice pieces");
                SetlistEntity setlist3 = new SetlistEntity("Recital Program", "Performance pieces for upcoming recital");

                long setlistId1 = setlistDao.insertSetlist(setlist1);
                long setlistId2 = setlistDao.insertSetlist(setlist2);
                long setlistId3 = setlistDao.insertSetlist(setlist3);


                // Insert sample sheets if needed for testing
                // SheetEntity sheet1 = new SheetEntity(...);
                // long sheetId1 = sheetDao.insertSheet(sheet1);
            });
        }
    };
}