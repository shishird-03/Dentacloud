package com.dentacloud.app.data.remote;

import androidx.annotation.Nullable;

import com.dentacloud.app.data.local.TokenStore;
import com.dentacloud.app.data.remote.api.AuthControllerApi;
import com.dentacloud.app.data.remote.dto.AuthResponse;
import com.dentacloud.app.data.remote.dto.RefreshRequest;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * On a 401, swaps the refresh token for a new pair once and retries (PRD 11). If the refresh token is
 * refused, the session is over and the app returns to login.
 */
@Singleton
public class TokenAuthenticator implements Authenticator {
    private final TokenStore tokens;
    private final AuthControllerApi auth; // built on the plain client, so this never recurses

    @Inject
    public TokenAuthenticator(TokenStore tokens, @Plain AuthControllerApi auth) {
        this.tokens = tokens;
        this.auth = auth;
    }

    @Nullable
    @Override
    public synchronized Request authenticate(@Nullable Route route, Response response) throws IOException {
        String sent = response.request().header("Authorization");
        if (sent == null || response.priorResponse() != null) return null; // no token, or already retried
        String current = tokens.access();
        if (current != null && !sent.equals("Bearer " + current)) {
            return retry(response, current); // another call refreshed while we waited for the lock
        }
        String refresh = tokens.refresh();
        if (refresh == null) return null;
        retrofit2.Response<AuthResponse> r = auth.authRefresh(new RefreshRequest().refreshToken(refresh)).execute();
        if (!r.isSuccessful() || r.body() == null) {
            if (r.code() == 401) tokens.markExpired();
            return null;
        }
        tokens.save(r.body().getAccessToken(), r.body().getRefreshToken());
        return retry(response, r.body().getAccessToken());
    }

    private static Request retry(Response response, String access) {
        return response.request().newBuilder().header("Authorization", "Bearer " + access).build();
    }
}
