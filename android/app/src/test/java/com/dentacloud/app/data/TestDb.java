package com.dentacloud.app.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Shared fixtures: in-memory Room, a logged-in session, and an executor that runs inline. */
public final class TestDb {
    public static final String CLINIC = "clinic-a";
    public static final String DENTIST = "dentist-1";

    private TestDb() {}

    public static AppDatabase memory() {
        return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    public static SessionStore session() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SessionStore s = new SessionStore(ctx);
        s.save("user-1", "Dr. Test", CLINIC, "Test Clinic", "OWNER", DENTIST, java.util.Set.of("patient.write"));
        return s;
    }

    /** Requires InstantTaskExecutorRule so Room's LiveData computes synchronously enough to read. */
    public static <T> T value(LiveData<T> live) {
        Object[] box = new Object[1];
        live.observeForever(v -> box[0] = v);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        for (int i = 0; i < 50 && box[0] == null; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
        }
        @SuppressWarnings("unchecked") T t = (T) box[0];
        return t;
    }

    public static ExecutorService direct() {
        return new AbstractExecutorService() {
            private boolean shut;
            @Override public void execute(Runnable r) { r.run(); }
            @Override public void shutdown() { shut = true; }
            @Override public List<Runnable> shutdownNow() { shut = true; return List.of(); }
            @Override public boolean isShutdown() { return shut; }
            @Override public boolean isTerminated() { return shut; }
            @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
        };
    }
}
