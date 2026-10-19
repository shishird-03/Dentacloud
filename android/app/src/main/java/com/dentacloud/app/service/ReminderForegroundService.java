package com.dentacloud.app.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.ui.MainActivity;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Part B #1: a service running in the background that watches today's appointments and raises a
 * reminder 15 minutes before each. Foreground (with its own quiet notification) so Android keeps it
 * alive during clinic hours; WorkManager is the fallback when it isn't running.
 */
@AndroidEntryPoint
public class ReminderForegroundService extends LifecycleService {
    private static final int ID = 1001;
    private static final long TICK_MS = 60_000;

    @Inject AppDatabase db;
    @Inject SessionStore session;
    @Inject ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            io.execute(() -> Reminders.check(getApplicationContext(), db, session, System.currentTimeMillis()));
            main.postDelayed(this, TICK_MS);
        }
    };

    public static void start(Context ctx) {
        ContextCompat.startForegroundService(ctx, new Intent(ctx, ReminderForegroundService.class));
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ReminderForegroundService.class));
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Reminders.createChannels(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, Reminders.CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_text))
                .setOngoing(true)
                .setContentIntent(open)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID, n);
        }
        main.removeCallbacks(tick);
        main.post(tick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        super.onDestroy();
    }
}
