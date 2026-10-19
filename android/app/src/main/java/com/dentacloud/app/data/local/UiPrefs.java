package com.dentacloud.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** Device preferences: theme and clinic location (Part B #9: save, retrieve, delete). */
@Singleton
public class UiPrefs {
    private static final String THEME = "theme"; // AppCompatDelegate night mode
    /** MG Road, Bengaluru: a placeholder until the clinic sets its own location. */
    private static final double DEFAULT_LAT = 12.9757, DEFAULT_LNG = 77.6050;
    private final SharedPreferences prefs;

    @Inject
    public UiPrefs(@ApplicationContext Context ctx) {
        prefs = ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE);
    }

    public int nightMode() {
        return prefs.getInt(THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void setNightMode(int mode) {
        prefs.edit().putInt(THEME, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public double clinicLat() {
        return Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(DEFAULT_LAT)));
    }

    public double clinicLng() {
        return Double.longBitsToDouble(prefs.getLong("lng", Double.doubleToLongBits(DEFAULT_LNG)));
    }

    public void setClinicLocation(double lat, double lng) {
        prefs.edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lng", Double.doubleToLongBits(lng)).apply();
    }

    public void reset() {
        prefs.edit().clear().apply();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void apply() {
        AppCompatDelegate.setDefaultNightMode(nightMode());
    }
}
