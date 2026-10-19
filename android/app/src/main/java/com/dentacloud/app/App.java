package com.dentacloud.app;

import android.app.Application;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.UiPrefs;
import com.dentacloud.app.data.sync.SyncScheduler;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class App extends Application {
    @Inject UiPrefs uiPrefs;
    @Inject SessionStore session;
    @Inject SyncScheduler sync;
    @Inject AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        uiPrefs.apply();
        com.dentacloud.app.service.Reminders.createChannels(this);
        if (session.isLoggedIn() && !session.isDemo()) sync.schedulePeriodic();
        // Any local write queues a pending_op; push it as soon as the network allows.
        db.pendingOps().count().observeForever(n -> {
            if (n != null && n > 0) sync.requestNow();
        });
    }
}
