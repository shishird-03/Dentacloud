package com.dentacloud.app.data.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** Schedules sync and exposes the status chip state (Synced, Syncing, Offline (N), Needs attention). */
@Singleton
public class SyncScheduler {
    public enum State { SYNCED, SYNCING, OFFLINE, ATTENTION, DEMO }

    public record Status(State state, int pending) {}

    private static final String NOW = "sync-now";
    private static final String PERIODIC = "sync-periodic";
    private static final Constraints ONLINE = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();

    private final WorkManager work;
    private final SessionStore session;
    private final MediatorLiveData<Status> status = new MediatorLiveData<>();
    private boolean running;
    private boolean online = true;
    private int pending;
    private int failed;

    @Inject
    public SyncScheduler(@ApplicationContext Context ctx, AppDatabase db, SessionStore session) {
        this.work = WorkManager.getInstance(ctx);
        this.session = session;
        LiveData<List<WorkInfo>> infos = work.getWorkInfosForUniqueWorkLiveData(NOW);
        status.addSource(infos, list -> {
            running = list.stream().anyMatch(i -> i.getState() == WorkInfo.State.RUNNING);
            publish();
        });
        status.addSource(db.pendingOps().count(), n -> { pending = n; publish(); });
        status.addSource(db.pendingOps().failedCount(), n -> { failed = n; publish(); });
        status.addSource(connectivity(ctx), up -> { online = up; publish(); });
    }

    public LiveData<Status> status() {
        return status;
    }

    public void schedulePeriodic() {
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES).setConstraints(ONLINE).build());
    }

    /** Runs a sync as soon as there is network; queued after any sync already running. */
    public void requestNow() {
        if (session.isDemo() || !session.isLoggedIn()) return;
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(SyncWorker.class).setConstraints(ONLINE)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }

    public void cancelAll() {
        work.cancelUniqueWork(NOW);
        work.cancelUniqueWork(PERIODIC);
    }

    private void publish() {
        State s;
        if (session.isDemo()) s = State.DEMO;
        else if (failed > 0) s = State.ATTENTION;
        else if (running) s = State.SYNCING;
        else if (!online || pending > 0) s = State.OFFLINE;
        else s = State.SYNCED;
        status.setValue(new Status(s, pending));
    }

    private static LiveData<Boolean> connectivity(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        MutableLiveData<Boolean> up = new MutableLiveData<>(cm.getActiveNetwork() != null);
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network n) { up.postValue(true); }
            @Override public void onLost(@NonNull Network n) { up.postValue(false); }
            @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
                up.postValue(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            }
        });
        return up;
    }
}
