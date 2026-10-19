package com.dentacloud.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.AppLock;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.UiPrefs;
import com.dentacloud.app.data.repo.ExportRepository;
import com.dentacloud.app.databinding.FragmentSettingsBinding;
import com.dentacloud.app.ui.common.Intents;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Theme, clinic location and the owner's data export (Part B #9 preferences, Part B #2 progress). */
@AndroidEntryPoint
public class SettingsFragment extends Fragment {
    @Inject UiPrefs prefs;
    @Inject SessionStore session;
    @Inject ExportRepository export;
    @Inject AppLock lock;
    private FragmentSettingsBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        b.clinic.setText(session.clinicName());
        showTheme();
        showLocation();
        b.theme.setOnCheckedChangeListener((g, id) -> {
            int mode = id == R.id.theme_light ? AppCompatDelegate.MODE_NIGHT_NO
                    : id == R.id.theme_dark ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (mode != prefs.nightMode()) prefs.setNightMode(mode);
        });
        b.saveLocation.setOnClickListener(v -> {
            try {
                double lat = Double.parseDouble(String.valueOf(b.lat.getText()));
                double lng = Double.parseDouble(String.valueOf(b.lng.getText()));
                if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
                prefs.setClinicLocation(lat, lng);
                Toast.makeText(requireContext(), R.string.location_saved, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), R.string.bad_location, Toast.LENGTH_SHORT).show();
            }
        });
        boolean canLock = BiometricManager.from(requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS;
        b.appLock.setChecked(lock.enabled());
        b.appLock.setEnabled(canLock || lock.enabled()); // can always be switched off
        b.appLockUnavailable.setVisibility(canLock ? View.GONE : View.VISIBLE);
        b.appLock.setOnCheckedChangeListener((s, on) -> lock.setEnabled(on));
        boolean canExport = session.can("data.export") || session.isDemo();
        b.export.setVisibility(canExport ? View.VISIBLE : View.GONE);
        b.exportLabel.setVisibility(canExport ? View.VISIBLE : View.GONE);
        b.export.setOnClickListener(v -> {
            b.export.setEnabled(false);
            b.exportProgress.setVisibility(View.VISIBLE);
            b.exportProgress.setProgress(0);
            export.exportPatients(
                    pct -> view.post(() -> { if (b != null) b.exportProgress.setProgressCompat(pct, true); }),
                    file -> view.post(() -> {
                        if (b == null) return;
                        b.export.setEnabled(true);
                        if (file == null) {
                            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".files", file);
                        Intent send = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Intents.start(requireContext(), Intent.createChooser(send, getString(R.string.export_patients)));
                    }));
        });
        b.reset.setOnClickListener(v -> {
            prefs.reset();
            showTheme();
            showLocation();
            Toast.makeText(requireContext(), R.string.prefs_reset, Toast.LENGTH_SHORT).show();
        });
    }

    private void showLocation() {
        b.lat.setText(String.format(Locale.ROOT, "%.5f", prefs.clinicLat()));
        b.lng.setText(String.format(Locale.ROOT, "%.5f", prefs.clinicLng()));
    }

    private void showTheme() {
        int mode = prefs.nightMode();
        b.theme.check(mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.theme_light
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.theme_dark : R.id.theme_system);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
