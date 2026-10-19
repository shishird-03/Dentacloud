package com.dentacloud.app.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavDeepLinkBuilder;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.ui.MainActivity;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Dates;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On-device reminders for today's appointments, shared by the foreground service and the WorkManager
 * fallback. Notification text carries the time only, never the patient (PRD 6, 14).
 */
public final class Reminders {
    public static final String CHANNEL_REMINDERS = "reminders";
    public static final String CHANNEL_SERVICE = "reminder-service";
    static final long LOOKAHEAD_MS = 15 * 60_000L;
    private static final String PREFS = "reminders";

    private Reminders() {}

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_REMINDERS,
                ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_SERVICE,
                ctx.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW));
    }

    public static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean("enabled", false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("enabled", on).apply();
    }

    /**
     * Notifies each BOOKED appointment starting within the next 15 minutes, once per day.
     * Blocking (database): call off the main thread. Returns how many notifications were posted.
     */
    public static int check(Context ctx, AppDatabase db, SessionStore session, long now) {
        if (!session.isLoggedIn()) return 0;
        List<AppointmentEntity> due = db.appointments().bookedBetween(session.clinicId(), now, now + LOOKAHEAD_MS);
        SharedPreferences p = prefs(ctx);
        String today = LocalDate.now(Dates.zone()).toString();
        Set<String> sent = new HashSet<>(today.equals(p.getString("day", "")) ? p.getStringSet("sent", Set.of()) : Set.of());
        int posted = 0;
        for (AppointmentEntity a : due) {
            if (!sent.add(a.id)) continue;
            notify(ctx, a);
            posted++;
        }
        p.edit().putString("day", today).putStringSet("sent", sent).apply();
        return posted;
    }

    /** Part B #7: tapping the notification opens the appointment detail screen. */
    static void notify(Context ctx, AppointmentEntity a) {
        PendingIntent open = new NavDeepLinkBuilder(ctx)
                .setComponentName(MainActivity.class)
                .setGraph(R.navigation.nav_graph)
                .setDestination(R.id.appointmentDetailFragment)
                .setArguments(Args.of(Args.APPOINTMENT_ID, a.id))
                .createPendingIntent();
        NotificationCompat.Builder n = new NotificationCompat.Builder(ctx, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.reminder_title))
                .setContentText(ctx.getString(R.string.reminder_text, Dates.time(a.startAt)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(ctx).notify(a.id.hashCode(), n.build());
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
