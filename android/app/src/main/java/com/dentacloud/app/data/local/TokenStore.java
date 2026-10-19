package com.dentacloud.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Access and refresh tokens in EncryptedSharedPreferences backed by the Android Keystore (PRD 9).
 * security-crypto is deprecated upstream but still the PRD's choice; swapping it only touches open().
 */
@Singleton
public class TokenStore {
    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> expired = new MutableLiveData<>(false);

    @Inject
    public TokenStore(@ApplicationContext Context ctx) {
        this(open(ctx));
    }

    /** Tests pass plain prefs: Robolectric has no AndroidKeyStore. */
    public TokenStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences open(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            return EncryptedSharedPreferences.create(ctx, "tokens", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Android Keystore unavailable", e);
        }
    }

    public synchronized void save(String access, String refresh) {
        prefs.edit().putString("access", access).putString("refresh", refresh).apply();
        expired.postValue(false);
    }

    public synchronized String access() { return prefs.getString("access", null); }
    public synchronized String refresh() { return prefs.getString("refresh", null); }

    public boolean hasSession() {
        return refresh() != null;
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }

    /** The refresh token was rejected: the user must log in again. */
    public void markExpired() {
        clear();
        expired.postValue(true);
    }

    public LiveData<Boolean> expired() {
        return expired;
    }
}
