package com.dentacloud.app.ui.common;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

/** Opens the Android share sheet for a generated PDF (receipts, prescriptions). */
public final class Share {
    private Share() {}

    public static void pdf(Context ctx, File file, String title) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intents.start(ctx, Intent.createChooser(send, title));
    }
}
