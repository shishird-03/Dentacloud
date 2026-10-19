package com.dentacloud.app.data.remote;

import androidx.annotation.NonNull;

import com.dentacloud.app.data.local.TokenStore;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Adds the access token to every API call except the auth endpoints themselves. */
@Singleton
public class AuthInterceptor implements Interceptor {
    private final TokenStore tokens;

    @Inject
    public AuthInterceptor(TokenStore tokens) {
        this.tokens = tokens;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request req = chain.request();
        String access = tokens.access();
        if (access == null || req.url().encodedPath().contains("/auth/") || req.header("Authorization") != null) {
            return chain.proceed(req);
        }
        return chain.proceed(req.newBuilder().header("Authorization", "Bearer " + access).build());
    }
}
