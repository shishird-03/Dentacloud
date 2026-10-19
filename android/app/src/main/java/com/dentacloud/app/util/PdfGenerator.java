package com.dentacloud.app.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Receipt and prescription PDFs drawn on the device (PRD 7: Android PdfDocument), A5 portrait with a
 * clinic letterhead. Files go to cache/pdfs and are shared through FileProvider.
 */
public final class PdfGenerator {
    /** One printable row: left text and optional right-aligned text (amounts). */
    public record Row(String left, String right, boolean bold) {
        public static Row of(String left) { return new Row(left, null, false); }
        public static Row money(String left, long paise, boolean bold) { return new Row(left, Money.format(paise), bold); }
    }

    private static final int WIDTH = 420, HEIGHT = 595, MARGIN = 32; // A5 at 72 dpi

    private PdfGenerator() {}

    /** Writes a document with letterhead, title and rows; returns the file. Blocking: call off the main thread. */
    public static File write(Context ctx, String fileName, String clinicName, String clinicLine, String title,
                             List<Row> rows, String footer) throws IOException {
        PdfDocument doc = new PdfDocument();
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(10);
        Paint bold = new Paint(text);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        Paint head = new Paint(bold);
        head.setTextSize(16);
        head.setColor(Color.rgb(15, 118, 110));
        Paint rule = new Paint();
        rule.setColor(Color.rgb(203, 213, 225));

        List<Row> remaining = new ArrayList<>(rows);
        int pageNo = 1;
        do {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create());
            Canvas c = page.getCanvas();
            float y = MARGIN + 16;
            c.drawText(clinicName, MARGIN, y, head);
            y += 14;
            if (clinicLine != null) c.drawText(clinicLine, MARGIN, y, text);
            y += 10;
            c.drawLine(MARGIN, y, WIDTH - MARGIN, y, rule);
            y += 22;
            c.drawText(title, MARGIN, y, bold);
            y += 20;
            while (!remaining.isEmpty() && y < HEIGHT - MARGIN - 30) {
                Row r = remaining.remove(0);
                Paint p = r.bold() ? bold : text;
                c.drawText(fit(r.left(), p, r.right() == null ? WIDTH - 2 * MARGIN : WIDTH - 2 * MARGIN - 90), MARGIN, y, p);
                if (r.right() != null) c.drawText(r.right(), WIDTH - MARGIN - p.measureText(r.right()), y, p);
                y += 16;
            }
            if (remaining.isEmpty() && footer != null) {
                c.drawLine(MARGIN, HEIGHT - MARGIN - 18, WIDTH - MARGIN, HEIGHT - MARGIN - 18, rule);
                c.drawText(footer, MARGIN, HEIGHT - MARGIN, text);
            }
            doc.finishPage(page);
            pageNo++;
        } while (!remaining.isEmpty());

        File dir = new File(ctx.getCacheDir(), "pdfs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, fileName);
        try (FileOutputStream os = new FileOutputStream(out)) {
            doc.writeTo(os);
        } finally {
            doc.close();
        }
        return out;
    }

    private static String fit(String s, Paint p, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, n) + "…";
    }
}
