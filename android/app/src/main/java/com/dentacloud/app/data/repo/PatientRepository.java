package com.dentacloud.app.data.repo;

import androidx.lifecycle.LiveData;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.sync.PendingOps;
import com.dentacloud.app.util.Ids;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PatientRepository {
    public static final String ENTITY = "patient";

    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public PatientRepository(AppDatabase db, SessionStore session, ExecutorService io) {
        this.db = db;
        this.session = session;
        this.io = io;
    }

    public LiveData<List<PatientEntity>> search(String q) {
        return db.patients().search(session.clinicId(), q == null ? "" : q.trim());
    }

    public LiveData<PatientEntity> observe(String id) {
        return db.patients().observe(id);
    }

    public void load(String id, Consumer<PatientEntity> onLoaded) {
        io.execute(() -> onLoaded.accept(db.patients().get(id)));
    }

    /** Patients already registered with this phone; the form shows a duplicate warning. */
    public void duplicates(String phone, String excludeId, Consumer<List<PatientEntity>> onResult) {
        io.execute(() -> onResult.accept(db.patients().withPhone(session.clinicId(), phone, excludeId)));
    }

    /** Insert or update. New patients get an id and a readable patient number. */
    public void save(PatientEntity p, Consumer<PatientEntity> onSaved) {
        io.execute(() -> {
            db.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                if (p.id.isEmpty()) {
                    p.id = Ids.uuid7();
                    p.clinicId = session.clinicId();
                    // ponytail: count-based numbers can collide across offline devices; server renumbers on push conflict
                    p.patientNo = String.format(Locale.ROOT, "P-%04d", db.patients().countAll(p.clinicId) + 1);
                    p.createdAt = now;
                }
                p.updatedAt = now;
                p.version++;
                db.patients().upsert(p);
                PendingOps.enqueue(db, ENTITY, p.id, p);
            });
            onSaved.accept(p);
        });
    }

    public void setColor(String id, int color) {
        update(id, p -> p.cardColor = color);
    }

    public void delete(String id) {
        update(id, p -> p.deletedAt = System.currentTimeMillis());
    }

    private void update(String id, Consumer<PatientEntity> change) {
        io.execute(() -> {
            PatientEntity p = db.patients().get(id);
            if (p == null) return;
            change.accept(p);
            save(p, saved -> {});
        });
    }
}
