package com.dentacloud.app.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/** WorkManager entry point; the real work is SyncEngine. */
public class SyncWorker extends Worker {
    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface Deps {
        SyncEngine engine();
    }

    public SyncWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            EntryPointAccessors.fromApplication(getApplicationContext(), Deps.class).engine().run();
            return Result.success();
        } catch (IOException e) {
            return getRunAttemptCount() < 5 ? Result.retry() : Result.failure();
        }
    }
}
