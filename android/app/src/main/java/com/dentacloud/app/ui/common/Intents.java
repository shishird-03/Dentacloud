package com.dentacloud.app.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.dentacloud.app.R;

/** Part A #6 and #7: SMS and phone call through system apps (no SMS/CALL permissions needed). */
public final class Intents {
    private Intents() {}

    public static void call(Context ctx, String phone) {
        start(ctx, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
    }

    /** Body must never carry health details (PRD 6, 14). */
    public static void sms(Context ctx, String phone, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
        i.putExtra("sms_body", body);
        start(ctx, i);
    }

    public static void start(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        }
    }
}
