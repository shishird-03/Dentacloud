package com.dentacloud.app.ui.common;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Part A #9: every destructive action (cancel, delete, refund, logout) goes through this. */
public final class Dialogs {
    private Dialogs() {}

    public static void confirm(Context ctx, String title, String message, Runnable onYes) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onYes.run())
                .show();
    }
}
