package com.dentacloud.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Who is logged in (Part B #9: SharedPreferences save, retrieve, delete).
 * Only non-secret identifiers live here; tokens go to EncryptedSharedPreferences (TokenStore).
 */
@Singleton
public class SessionStore {
    /** The offline demo clinic: never synced. */
    public static final String DEMO_CLINIC_ID = "0192f000-0000-7000-8000-0000000000c1";

    private static final String FILE = "session";
    private final SharedPreferences prefs;

    @Inject
    public SessionStore(@ApplicationContext Context ctx) {
        prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public void save(String userId, String userName, String clinicId, String clinicName, String role,
                     String memberId, Set<String> scopes) {
        prefs.edit()
                .putString("userId", userId)
                .putString("userName", userName)
                .putString("clinicId", clinicId)
                .putString("clinicName", clinicName)
                .putString("role", role)
                .putString("memberId", memberId)
                .putStringSet("scopes", new HashSet<>(scopes))
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.contains("userId");
    }

    public boolean isDemo() {
        return DEMO_CLINIC_ID.equals(clinicId());
    }

    public String userId() { return prefs.getString("userId", ""); }
    public String userName() { return prefs.getString("userName", ""); }
    public String clinicId() { return prefs.getString("clinicId", ""); }
    public String clinicName() { return prefs.getString("clinicName", ""); }
    public String role() { return prefs.getString("role", ""); }

    /** clinic_member id: appointments reference the member (dentist), not the login. */
    public String memberId() { return prefs.getString("memberId", userId()); }

    /** UI hint only; the server enforces every scope. */
    public boolean can(String scope) {
        return prefs.getStringSet("scopes", Set.of()).contains(scope);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
