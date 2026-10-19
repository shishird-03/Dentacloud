package com.dentacloud.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.function.Consumer;

/**
 * Part B #3: battery broadcast receiver. Registered by MainActivity while visible; reports whether
 * the tablet is low on battery and not charging, which shows the dashboard warning banner.
 */
public class BatteryReceiver extends BroadcastReceiver {
    public static final int LOW_PERCENT = 20;

    private final Consumer<Boolean> onLowChanged;

    public BatteryReceiver(Consumer<Boolean> onLowChanged) {
        this.onLowChanged = onLowChanged;
    }

    public static IntentFilter filter() {
        return new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        onLowChanged.accept(isLow(intent));
    }

    static boolean isLow(Intent battery) {
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        boolean plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        return level >= 0 && scale > 0 && !plugged && level * 100 / scale <= LOW_PERCENT;
    }
}
