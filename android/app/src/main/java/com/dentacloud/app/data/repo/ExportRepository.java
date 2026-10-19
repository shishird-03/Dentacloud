package com.dentacloud.app.data.repo;

import android.content.Context;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PatientEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Owner data export (F12) to CSV, with progress. Runs on the shared ExecutorService: this is the
 * production replacement for the lab's AsyncTask demo (PRD 5, Part B #2).
 */
@Singleton
public class ExportRepository {
    private final Context ctx;
    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public ExportRepository(@ApplicationContext Context ctx, AppDatabase db, SessionStore session, ExecutorService io) {
        this.ctx = ctx;
        this.db = db;
        this.session = session;
        this.io = io;
    }

    /** progress gets 0..100; done gets the file, or null if writing failed. Both run on the worker thread. */
    public void exportPatients(IntConsumer progress, Consumer<File> done) {
        io.execute(() -> {
            List<PatientEntity> all = db.patients().allNow(session.clinicId());
            File dir = new File(ctx.getCacheDir(), "exports");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "patients.csv");
            try (Writer w = new FileWriter(out)) {
                w.write("patient_no,full_name,dob,gender,phone,email,allergies\n");
                for (int i = 0; i < all.size(); i++) {
                    PatientEntity p = all.get(i);
                    w.write(String.join(",", csv(p.patientNo), csv(p.fullName), csv(p.dob), csv(p.gender),
                            csv(p.phone), csv(p.email), csv(p.allergies)) + "\n");
                    progress.accept((i + 1) * 100 / all.size());
                }
                if (all.isEmpty()) progress.accept(100);
                done.accept(out);
            } catch (IOException e) {
                done.accept(null);
            }
        });
    }

    /** RFC 4180 quoting; a leading =, +, - or @ is defused so spreadsheets don't run it as a formula. */
    static String csv(String s) {
        if (s == null) return "";
        String v = s.matches("^[=+\\-@].*") ? "'" + s : s;
        return v.matches(".*[\",\\n\\r].*") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
