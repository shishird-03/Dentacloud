package com.dentacloud.app.data.repo;

import androidx.lifecycle.LiveData;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.sync.PendingOps;
import com.dentacloud.app.util.Dates;
import com.dentacloud.app.util.Ids;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppointmentRepository {
    public static final String ENTITY = "appointment";

    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public AppointmentRepository(AppDatabase db, SessionStore session, ExecutorService io) {
        this.db = db;
        this.session = session;
        this.io = io;
    }

    /** Appointments starting on [from, from + days). */
    public LiveData<List<AppointmentRow>> range(LocalDate from, int days) {
        return db.appointments().between(session.clinicId(), Dates.startOfDay(from), Dates.startOfDay(from.plusDays(days)));
    }

    public LiveData<List<AppointmentRow>> forPatient(String patientId) {
        return db.appointments().forPatient(patientId);
    }

    public LiveData<AppointmentRow> observe(String id) {
        return db.appointments().observe(id);
    }

    /** Books for the logged-in dentist. Calls back with null on success or a user-facing error. */
    public void book(AppointmentEntity a, Consumer<String> onResult) {
        io.execute(() -> {
            if (a.endAt <= a.startAt) {
                onResult.accept("End time must be after start time");
                return;
            }
            if (a.dentistId.isEmpty()) a.dentistId = session.memberId();
            boolean[] clash = {false};
            db.runInTransaction(() -> {
                if (db.appointments().countOverlaps(a.dentistId, a.startAt, a.endAt, a.id) > 0) {
                    clash[0] = true;
                    return;
                }
                if (a.id.isEmpty()) {
                    a.id = Ids.uuid7();
                    a.clinicId = session.clinicId();
                }
                write(a);
            });
            onResult.accept(clash[0] ? "This dentist already has an appointment in that slot" : null);
        });
    }

    public void setStatus(String id, String status) {
        io.execute(() -> db.runInTransaction(() -> {
            AppointmentEntity a = db.appointments().get(id);
            if (a == null) return;
            a.status = status;
            write(a);
        }));
    }

    public void setColor(String id, int color) {
        io.execute(() -> db.runInTransaction(() -> {
            AppointmentEntity a = db.appointments().get(id);
            if (a == null) return;
            a.colorLabel = color;
            write(a);
        }));
    }

    private void write(AppointmentEntity a) {
        a.updatedAt = System.currentTimeMillis();
        a.version++;
        db.appointments().upsert(a);
        PendingOps.enqueue(db, ENTITY, a.id, a);
    }
}
