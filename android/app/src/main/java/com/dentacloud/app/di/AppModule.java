package com.dentacloud.app.di;

import android.content.Context;

import androidx.room.Room;

import com.dentacloud.app.data.local.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {
    private AppModule() {}

    @Provides
    @Singleton
    static AppDatabase database(@ApplicationContext Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, AppDatabase.NAME)
                .addMigrations(Migrations.ALL)
                .build();
    }

    /** All disk and network work runs here, never on the main thread. */
    @Provides
    @Singleton
    static ExecutorService io() {
        return Executors.newFixedThreadPool(4);
    }
}
