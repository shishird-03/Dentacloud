package com.dentacloud.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.dentacloud.app.util.PdfGenerator;
import com.dentacloud.app.util.PdfGenerator.Row;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** PdfDocument is native, so this runs on a device or emulator, not Robolectric. */
@RunWith(AndroidJUnit4.class)
public class PdfGeneratorTest {
    @Test
    public void writesAMultiPageReceipt() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) rows.add(Row.money("Line " + i, 100_00, false)); // forces a second page
        rows.add(Row.money("Total", 6_000_00, true));
        File f = PdfGenerator.write(ctx, "test-receipt.pdf", "Test Clinic", "MG Road, Bengaluru", "Receipt INV-00001",
                rows, "Thank you");
        byte[] head = new byte[5];
        System.arraycopy(Files.readAllBytes(f.toPath()), 0, head, 0, 5);
        assertTrue(new String(head, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertTrue(f.length() > 1000);
    }
}
