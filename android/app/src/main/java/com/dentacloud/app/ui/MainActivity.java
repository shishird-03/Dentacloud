package com.dentacloud.app.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.AppLock;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.TokenStore;
import com.dentacloud.app.data.repo.AuthRepository;
import com.dentacloud.app.data.sync.SyncScheduler;
import com.dentacloud.app.receiver.BatteryReceiver;
import com.dentacloud.app.databinding.ActivityMainBinding;

import java.util.Set;

import javax.inject.Inject;

import androidx.navigation.NavOptions;
import dagger.hilt.android.AndroidEntryPoint;

/** Single activity; every screen is a Navigation destination (PRD 9). */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private static final Set<Integer> TOP_LEVEL = Set.of(R.id.dashboardFragment, R.id.patientListFragment,
            R.id.calendarFragment, R.id.moreFragment, R.id.loginFragment, R.id.splashFragment);
    /** Screens with no patient data; every other screen gets FLAG_SECURE (no screenshots or recents preview). */
    private static final Set<Integer> NO_PHI = Set.of(R.id.splashFragment, R.id.loginFragment, R.id.signupFragment,
            R.id.moreFragment, R.id.settingsFragment, R.id.tipsFragment, R.id.clinicMapFragment, R.id.asyncTaskLabFragment);

    @Inject SyncScheduler sync;
    @Inject TokenStore tokens;
    @Inject SessionStore session;
    @Inject AppLock lock;
    @Inject AuthRepository auth;
    private ActivityMainBinding b;
    private NavController nav;
    private boolean chromeVisible;
    private BiometricPrompt prompt;
    private boolean prompting;
    private View lockCover;
    private final OnBackPressedCallback lockedBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            moveTaskToBack(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        nav = host.getNavController();
        NavigationUI.setupActionBarWithNavController(this, nav, new AppBarConfiguration.Builder(TOP_LEVEL).build());
        // Phones get bottom navigation; tablets (sw600dp layout) get a navigation rail instead.
        if (b.bottomNav != null) NavigationUI.setupWithNavController(b.bottomNav, nav);
        if (b.navRail != null) NavigationUI.setupWithNavController(b.navRail, nav);

        nav.addOnDestinationChangedListener((c, dest, args) -> {
            int id = dest.getId();
            chromeVisible = id != R.id.splashFragment && id != R.id.loginFragment && id != R.id.signupFragment;
            b.toolbar.setVisibility(chromeVisible ? View.VISIBLE : View.GONE);
            if (b.bottomNav != null) b.bottomNav.setVisibility(chromeVisible ? View.VISIBLE : View.GONE);
            if (b.navRail != null) b.navRail.setVisibility(chromeVisible ? View.VISIBLE : View.GONE);
            if (NO_PHI.contains(id)) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            invalidateOptionsMenu();
        });

        getOnBackPressedDispatcher().addCallback(this, lockedBack);
        prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                prompting = false;
                lock.unlocked();
                hideLock();
            }

            @Override
            public void onAuthenticationError(int code, @NonNull CharSequence message) {
                prompting = false;
                if (code == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || code == BiometricPrompt.ERROR_NO_BIOMETRICS
                        || code == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                    // The phone's screen lock was removed, so the prompt can never succeed: sign in again instead.
                    auth.logout();
                    lock.unlocked();
                    hideLock();
                    nav.navigate(R.id.loginFragment, null, new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
                }
                // Otherwise (cancelled, too many tries) the cover stays and Unlock tries again.
            }
        });

        sync.status().observe(this, this::showSync);
        b.syncChip.setOnClickListener(v -> {
            sync.requestNow();
            Toast.makeText(this, R.string.sync_requested, Toast.LENGTH_SHORT).show();
        });
        tokens.expired().observe(this, expired -> {
            if (!expired) return;
            Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show();
            nav.navigate(R.id.loginFragment, null, new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
        });
    }

    /** Part B #3: battery broadcasts drive the low-battery banner while the app is visible. */
    private final BatteryReceiver battery = new BatteryReceiver(this::setBatteryLow);

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, battery, BatteryReceiver.filter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (lock.mustUnlock(SystemClock.elapsedRealtime(), session.isLoggedIn())) showLock();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(battery);
        lock.onBackground(SystemClock.elapsedRealtime());
        super.onStop();
    }

    private void showLock() {
        if (lockCover == null) {
            lockCover = getLayoutInflater().inflate(R.layout.view_app_lock, null);
            lockCover.findViewById(R.id.unlock).setOnClickListener(v -> authenticate());
            addContentView(lockCover, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        lockCover.setVisibility(View.VISIBLE);
        lockedBack.setEnabled(true);
        authenticate();
    }

    private void hideLock() {
        if (lockCover != null) lockCover.setVisibility(View.GONE);
        lockedBack.setEnabled(false);
    }

    /** Device PIN or pattern is allowed too, so a failed fingerprint sensor never locks staff out. */
    private void authenticate() {
        if (prompting) return; // e.g. back from the device-credential screen, result not yet delivered
        prompting = true;
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build());
    }

    private void showSync(SyncScheduler.Status s) {
        b.syncChip.setText(switch (s.state()) {
            case SYNCED -> getString(R.string.sync_synced);
            case SYNCING -> getString(R.string.sync_syncing);
            case OFFLINE -> s.pending() > 0 ? getString(R.string.sync_offline, s.pending()) : getString(R.string.sync_offline_none);
            case ATTENTION -> getString(R.string.sync_attention);
            case DEMO -> getString(R.string.sync_demo);
        });
    }

    /** Part A #4: options menu that switches between Patients and Appointments. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (chromeVisible) getMenuInflater().inflate(R.menu.main_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return NavigationUI.onNavDestinationSelected(item, nav) || super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return nav.navigateUp() || super.onSupportNavigateUp();
    }

    public void setBatteryLow(boolean low) {
        b.batteryBanner.setVisibility(low ? View.VISIBLE : View.GONE);
    }
}
