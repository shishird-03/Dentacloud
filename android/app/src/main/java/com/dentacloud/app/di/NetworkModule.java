package com.dentacloud.app.di;

import com.dentacloud.app.BuildConfig;
import com.dentacloud.app.data.remote.AuthInterceptor;
import com.dentacloud.app.data.remote.Plain;
import com.dentacloud.app.data.remote.TokenAuthenticator;
import com.dentacloud.app.data.remote.api.AuthControllerApi;
import com.dentacloud.app.data.remote.api.SyncControllerApi;
import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/** Retrofit over the generated interfaces (data/remote/api), never hand-written endpoints. */
@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {
    private NetworkModule() {}

    @Provides
    @Singleton
    static Gson gson() {
        return new Gson();
    }

    @Provides
    @Singleton
    @Plain
    static OkHttpClient plainClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Provides
    @Singleton
    static OkHttpClient client(@Plain OkHttpClient plain, AuthInterceptor auth, TokenAuthenticator refresh) {
        return plain.newBuilder().addInterceptor(auth).authenticator(refresh).build();
    }

    @Provides
    @Singleton
    @Plain
    static Retrofit plainRetrofit(@Plain OkHttpClient client, Gson gson) {
        return retrofit(client, gson);
    }

    @Provides
    @Singleton
    static Retrofit authedRetrofit(OkHttpClient client, Gson gson) {
        return retrofit(client, gson);
    }

    @Provides
    @Singleton
    @Plain
    static AuthControllerApi authApi(@Plain Retrofit r) {
        return r.create(AuthControllerApi.class);
    }

    @Provides
    @Singleton
    static SyncControllerApi syncApi(Retrofit r) {
        return r.create(SyncControllerApi.class);
    }

    private static Retrofit retrofit(OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }
}
