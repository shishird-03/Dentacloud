package com.dentacloud.app.data.repo;

import androidx.lifecycle.LiveData;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PrescriptionEntity;
import com.dentacloud.app.data.local.entity.PrescriptionItemEntity;
import com.dentacloud.app.data.sync.PendingOps;
import com.dentacloud.app.util.Ids;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Prescriptions (F9): issued once, never edited. */
@Singleton
public class PrescriptionRepository {
    /** Common dental drugs with sensible defaults; the favourites row shows the first few. */
    public static final String[][] CATALOG = {
            {"Amoxicillin 500 mg", "1 capsule", "Three times a day", "5"},
            {"Ibuprofen 400 mg", "1 tablet", "Twice a day after food", "3"},
            {"Paracetamol 650 mg", "1 tablet", "When needed, max 4 a day", "3"},
            {"Metronidazole 400 mg", "1 tablet", "Three times a day", "5"},
            {"Amoxicillin + Clavulanate 625 mg", "1 tablet", "Twice a day", "5"},
            {"Chlorhexidine 0.2% mouthwash", "10 ml", "Rinse twice a day", "7"},
            {"Pantoprazole 40 mg", "1 tablet", "Once a day before breakfast", "5"},
            {"Ketorolac 10 mg", "1 tablet", "When needed for severe pain", "2"},
    };

    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public PrescriptionRepository(AppDatabase db, SessionStore session, ExecutorService io) {
        this.db = db;
        this.session = session;
        this.io = io;
    }

    public LiveData<List<PrescriptionEntity>> forPatient(String patientId) {
        return db.prescriptions().forPatient(patientId);
    }

    public void items(String prescriptionId, Consumer<List<PrescriptionItemEntity>> onLoaded) {
        io.execute(() -> onLoaded.accept(db.prescriptions().items(prescriptionId)));
    }

    public void issue(String patientId, String visitId, String notes, List<PrescriptionItemEntity> items,
                      Consumer<PrescriptionEntity> onSaved) {
        io.execute(() -> {
            PrescriptionEntity rx = new PrescriptionEntity();
            rx.id = Ids.uuid7();
            rx.clinicId = session.clinicId();
            rx.patientId = patientId;
            rx.visitId = visitId;
            rx.dentistId = session.memberId();
            rx.issuedAt = System.currentTimeMillis();
            rx.notes = notes;
            for (PrescriptionItemEntity it : items) {
                it.id = Ids.uuid7();
                it.prescriptionId = rx.id;
            }
            db.runInTransaction(() -> {
                db.prescriptions().upsert(rx);
                db.prescriptions().upsertItems(items);
                PendingOps.enqueue(db, "prescription", rx.id, rx, "items", items);
            });
            onSaved.accept(rx);
        });
    }
}
