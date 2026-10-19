package com.dentacloud.app.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;

import java.util.concurrent.TimeUnit;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/** Fallback reminder check every 15 minutes (WorkManager's minimum) when the service isn't running. */
public class ReminderWorker extends Worker {
    static final String NAME = "reminders";

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface Deps {
        AppDatabase db();
        SessionStore session();
    }

    public ReminderWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    public static void schedule(Context ctx) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(ReminderWorker.class, 15, TimeUnit.MINUTES).build());
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        Deps d = EntryPointAccessors.fromApplication(getApplicationContext(), Deps.class);
        Reminders.check(getApplicationContext(), d.db(), d.session(), System.currentTimeMillis());
        return Result.success();
    }
}
