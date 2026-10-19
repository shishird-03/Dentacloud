package com.dentacloud.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dentacloud.app.service.ReminderWorker;
import com.dentacloud.app.service.Reminders;

/** After a reboot, reminders resume through WorkManager (Android blocks most foreground starts from boot). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Reminders.enabled(ctx)) {
            ReminderWorker.schedule(ctx);
        }
    }
}
