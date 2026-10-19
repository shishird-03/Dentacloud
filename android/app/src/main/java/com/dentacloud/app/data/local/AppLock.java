package com.dentacloud.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Optional biometric app lock (PRD 9, 14). Locked on a cold start and after the app has been in
 * the background for {@link #TIMEOUT_MS}; rotation and quick trips to the camera or dialer don't
 * relock. Times are {@code SystemClock.elapsedRealtime()}, passed in so tests control the clock.
 * Kept out of UiPrefs so "reset preferences" can't silently switch the lock off.
 */
@Singleton
public class AppLock {
    public static final long TIMEOUT_MS = 60_000;
    private final SharedPreferences prefs;
    private boolean locked = true;
    private long backgroundedAt;

    @Inject
    public AppLock(@ApplicationContext Context ctx) {
        this(ctx.getSharedPreferences("app_lock", Context.MODE_PRIVATE));
    }

    public AppLock(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public boolean enabled() {
        return prefs.getBoolean("enabled", false);
    }

    /** Turning the lock on doesn't lock the person who just turned it on. */
    public synchronized void setEnabled(boolean on) {
        prefs.edit().putBoolean("enabled", on).apply();
        unlocked();
    }

    public synchronized void onBackground(long now) {
        if (!locked) backgroundedAt = now;
    }

    public synchronized boolean mustUnlock(long now, boolean signedIn) {
        if (!enabled() || !signedIn) return false;
        if (!locked && backgroundedAt > 0 && now - backgroundedAt >= TIMEOUT_MS) locked = true;
        return locked;
    }

    public synchronized void unlocked() {
        locked = false;
        backgroundedAt = 0;
    }
}
