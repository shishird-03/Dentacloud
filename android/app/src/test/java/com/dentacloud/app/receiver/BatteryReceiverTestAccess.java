package com.dentacloud.app.receiver;

import android.content.Intent;

/** Exposes the package-private battery rule to tests in other packages. */
public final class BatteryReceiverTestAccess {
    private BatteryReceiverTestAccess() {}

    public static boolean isLow(Intent battery) {
        return BatteryReceiver.isLow(battery);
    }
}
