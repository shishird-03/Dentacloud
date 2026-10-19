package com.dentacloud.app.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.dentacloud.app.util.Ids;

import java.io.File;

/** Photos live in app-private storage (excluded from backup) and are shared via FileProvider. */
public final class Photos {
    private Photos() {}

    public static File newFile(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, Ids.uuid7() + ".jpg");
    }

    public static Uri uriFor(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".files", f);
    }

    /** Downsampled decode so a 12 MP photo doesn't blow the heap on a 3 GB phone. */
    public static Bitmap thumbnail(String path, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, o);
    }
}
