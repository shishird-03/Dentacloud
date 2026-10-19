package com.dentacloud.app.data.repo;

import android.os.Build;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.TokenStore;
import com.dentacloud.app.data.remote.Plain;
import com.dentacloud.app.data.remote.api.AuthControllerApi;
import com.dentacloud.app.data.remote.dto.AuthResponse;
import com.dentacloud.app.data.remote.dto.LoginRequest;
import com.dentacloud.app.data.remote.dto.RefreshRequest;
import com.dentacloud.app.data.remote.dto.SignupRequest;
import com.dentacloud.app.data.sync.SyncScheduler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Response;

@Singleton
public class AuthRepository {
    public static final String DEMO_EMAIL = "owner@demo.clinic";
    public static final String DEMO_PASSWORD = "demo1234";
    static final String DEMO_USER_ID = "0192f000-0000-7000-8000-000000000001";
    private static final Set<String> DEMO_SCOPES = Set.of("patient.read", "patient.write", "appointment.read",
            "appointment.write", "appointment.status", "invoice.read", "invoice.write", "chart.read", "chart.write",
            "plan.read", "plan.write", "prescription.read", "prescription.write", "clinic.admin");

    private final SessionStore session;
    private final TokenStore tokens;
    private final BillingRepository billing;
    private final AppDatabase db;
    private final AuthControllerApi api;
    private final SyncScheduler sync;
    private final ExecutorService io;

    @Inject
    public AuthRepository(SessionStore session, TokenStore tokens, BillingRepository billing, AppDatabase db,
                          @Plain AuthControllerApi api, SyncScheduler sync, ExecutorService io) {
        this.session = session;
        this.tokens = tokens;
        this.billing = billing;
        this.db = db;
        this.api = api;
        this.sync = sync;
        this.io = io;
    }

    /** Calls back with null on success, otherwise a user-facing error. */
    public void login(String email, String password, Consumer<String> onResult) {
        call(api.authLogin(new LoginRequest().email(email.trim()).password(password).deviceName(Build.MODEL)), onResult);
    }

    /** Onboarding step 1: creates the clinic and its owner account, then logs in. */
    public void signup(String clinicName, String ownerName, String email, String phone, String password,
                       Consumer<String> onResult) {
        SignupRequest req = new SignupRequest().clinicName(clinicName.trim()).ownerName(ownerName.trim())
                .email(email.trim()).password(password).deviceName(Build.MODEL);
        if (!phone.isBlank()) req.phone(phone.replace(" ", ""));
        call(api.authSignup(req), onResult);
    }

    /** Offline demo clinic for viva and lab use: local data only, never synced. */
    public void loginDemo(Consumer<String> onResult) {
        io.execute(() -> {
            if (!SessionStore.DEMO_CLINIC_ID.equals(session.clinicId())) db.clearAllTables();
            tokens.clear();
            session.save(DEMO_USER_ID, "Dr. Meera", SessionStore.DEMO_CLINIC_ID, "Smile Care Dental (demo)", "OWNER",
                    DEMO_USER_ID, DEMO_SCOPES);
            billing.seedCatalogIfEmpty();
            onResult.accept(null);
        });
    }

    public void logout() {
        String refresh = tokens.refresh();
        sync.cancelAll();
        tokens.clear();
        session.clear();
        if (refresh != null) {
            io.execute(() -> {
                try {
                    api.authLogout(new RefreshRequest().refreshToken(refresh)).execute();
                } catch (IOException ignored) {
                    // offline: the token simply expires
                }
            });
        }
    }

    private void call(Call<AuthResponse> call, Consumer<String> onResult) {
        io.execute(() -> {
            try {
                Response<AuthResponse> r = call.execute();
                if (r.isSuccessful() && r.body() != null) {
                    start(r.body());
                    onResult.accept(null);
                } else {
                    onResult.accept(switch (r.code()) {
                        case 401 -> "Wrong email or password";
                        case 409 -> "An account with this email already exists";
                        case 400 -> "Please check the details and try again";
                        default -> "Server error (" + r.code() + "). Try again shortly.";
                    });
                }
            } catch (IOException e) {
                onResult.accept("Can't reach the server. Check the connection, or try the offline demo.");
            }
        });
    }

    private void start(AuthResponse a) {
        // Never mix two clinics' data on one device.
        if (!a.getClinicId().toString().equals(session.clinicId())) db.clearAllTables();
        tokens.save(a.getAccessToken(), a.getRefreshToken());
        session.save(a.getUserId().toString(), a.getUserName(), a.getClinicId().toString(), a.getClinicName(),
                a.getRole(), a.getMemberId().toString(), new HashSet<>(a.getScopes()));
        sync.schedulePeriodic();
        sync.requestNow();
    }
}
