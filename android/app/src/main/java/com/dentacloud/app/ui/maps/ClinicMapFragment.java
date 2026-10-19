package com.dentacloud.app.ui.maps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.dentacloud.app.BuildConfig;
import com.dentacloud.app.R;
import com.dentacloud.app.data.local.UiPrefs;
import com.dentacloud.app.ui.common.Intents;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Course objective 5: clinic location on an OpenStreetMap map (osmdroid, no API key) and a Navigate
 * button that hands over to the phone's navigation app.
 */
@AndroidEntryPoint
public class ClinicMapFragment extends Fragment {
    @Inject UiPrefs prefs;
    private MapView map;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        Context ctx = requireContext();
        // OSM's tile policy asks for an identifying user agent.
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        Configuration.getInstance().setOsmdroidBasePath(new java.io.File(ctx.getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new java.io.File(ctx.getCacheDir(), "osmdroid/tiles"));

        double lat = prefs.clinicLat(), lng = prefs.clinicLng();
        map = new MapView(ctx);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);
        GeoPoint clinic = new GeoPoint(lat, lng);
        map.getController().setCenter(clinic);
        Marker marker = new Marker(map);
        marker.setPosition(clinic);
        marker.setTitle(getString(R.string.clinic_location));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);

        MaterialButton navigate = new MaterialButton(ctx);
        navigate.setText(R.string.navigate);
        navigate.setOnClickListener(v -> navigate(ctx, lat, lng));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(map, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        int pad = getResources().getDimensionPixelSize(R.dimen.space_3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.touch));
        lp.setMargins(pad, pad, pad, pad);
        root.addView(navigate, lp);
        return root;
    }

    /** Turn-by-turn in Google Maps when installed, otherwise any app that handles geo: links. */
    static void navigate(Context ctx, double lat, double lng) {
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.ROOT, "google.navigation:q=%f,%f", lat, lng)));
        if (nav.resolveActivity(ctx.getPackageManager()) == null) {
            nav = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.ROOT, "geo:%f,%f?q=%f,%f(Clinic)", lat, lng, lat, lng)));
        }
        Intents.start(ctx, nav);
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }
}
