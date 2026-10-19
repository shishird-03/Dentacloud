package com.dentacloud.app.data;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;
import com.dentacloud.app.di.Migrations;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Every Room schema change ships with a tested migration (PRD 10). A real v1 database is built from
 * the exported schemas/1.json, then opened as v2: Room validates the migrated schema against v2 on
 * open and throws if the migration missed anything.
 * (Room's MigrationTestHelper trips over Windows paths under Robolectric, hence the manual setup.)
 */
@RunWith(RobolectricTestRunner.class)
public class MigrationTest {
    @Test
    public void v1ToV2KeepsDataAndAddsClinicalTables() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        File file = ctx.getDatabasePath("migration-test.db");
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        file.delete();

        JsonObject v1 = JsonParser.parseReader(new InputStreamReader(
                ctx.getAssets().open("com.dentacloud.app.data.local.AppDatabase/1.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("database");
        try (SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            for (JsonElement e : v1.getAsJsonArray("entities")) {
                JsonObject t = e.getAsJsonObject();
                String table = t.get("tableName").getAsString();
                raw.execSQL(t.get("createSql").getAsString().replace("${TABLE_NAME}", table));
                if (t.has("indices")) {
                    for (JsonElement ix : t.getAsJsonArray("indices")) {
                        raw.execSQL(ix.getAsJsonObject().get("createSql").getAsString().replace("${TABLE_NAME}", table));
                    }
                }
            }
            for (JsonElement q : v1.getAsJsonArray("setupQueries")) raw.execSQL(q.getAsString());
            raw.execSQL("INSERT INTO patient (id, clinicId, patientNo, fullName, cardColor, createdAt, updatedAt, version)"
                    + " VALUES ('p1', 'c1', 'P-0001', 'Kept Patient', 0, 1, 1, 1)");
            raw.setVersion(1);
        }

        AppDatabase db = Room.databaseBuilder(ctx, AppDatabase.class, "migration-test.db")
                .addMigrations(Migrations.ALL).allowMainThreadQueries().build();
        try {
            assertEquals("Kept Patient", db.patients().get("p1").fullName);
            PaymentEntryEntity p = new PaymentEntryEntity();
            p.id = "pay1";
            p.clinicId = "c1";
            p.invoiceId = "i1";
            p.amountPaise = 100;
            p.idempotencyKey = "k1";
            db.invoices().insertPayment(p);
            assertEquals(100, db.invoices().paid("i1"));
        } finally {
            db.close();
        }
    }
}
