package com.dentacloud.scheduling;

import com.dentacloud.audit.AuditService;
import com.dentacloud.billing.ProcedureDirectory;
import com.dentacloud.identity.StaffDirectory;
import com.dentacloud.patient.PatientDirectory;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Claims;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
class AppointmentService {
    private final AppointmentRepository repo;
    private final PatientDirectory patients;
    private final StaffDirectory staff;
    private final ProcedureDirectory procedures;
    private final AuditService audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    AppointmentService(AppointmentRepository repo, PatientDirectory patients, StaffDirectory staff,
                       ProcedureDirectory procedures, AuditService audit,
                       org.springframework.context.ApplicationEventPublisher events) {
        this.repo = repo;
        this.patients = patients;
        this.staff = staff;
        this.procedures = procedures;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    List<AppointmentDto> list(Instant from, Instant to, UUID dentistId) {
        List<Appointment> rows = dentistId == null
                ? repo.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(from, to)
                : repo.findByDentistIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(dentistId, from, to);
        return rows.stream().map(AppointmentService::dto).toList();
    }

    @Transactional(readOnly = true)
    List<AppointmentDto> load(Collection<UUID> ids) {
        return repo.findAllById(ids).stream().map(AppointmentService::dto).toList();
    }

    @Transactional(readOnly = true)
    boolean exists(UUID id) {
        return repo.existsById(id);
    }

    /**
     * Books a slot. Repeating a request with the same idempotency key (or the same client id) returns
     * the original booking. Overlaps are refused by the database's exclusion constraints, so two
     * simultaneous requests can never both succeed.
     */
    @Transactional
    AppointmentDto create(AppointmentDto in, String idempotencyKey, String source) {
        var existing = idempotencyKey != null ? repo.findByIdempotencyKey(idempotencyKey)
                : in.id() == null ? java.util.Optional.<Appointment>empty() : repo.findById(in.id());
        if (existing.isPresent()) return dto(existing.get());

        if (!patients.exists(in.patientId())) throw ApiException.badRequest("unknown_patient", "Unknown patient");
        if (!staff.isActiveDentist(in.dentistId())) throw ApiException.badRequest("unknown_dentist", "Not an active dentist of this clinic");
        if (in.chairId() != null && !staff.isActiveChair(in.chairId())) throw ApiException.badRequest("unknown_chair", "Unknown chair");

        Appointment a = new Appointment();
        a.id = in.id() == null ? Ids.next() : in.id();
        a.clinicId = TenantContext.require();
        a.patientId = in.patientId();
        a.dentistId = in.dentistId();
        a.chairId = in.chairId();
        a.procedureId = in.procedureId();
        a.startAt = Instant.ofEpochMilli(in.startAt());
        long end = in.endAt() > 0 ? in.endAt() : in.startAt() + procedures.minutesFor(in.procedureId()) * 60_000L;
        a.endAt = Instant.ofEpochMilli(end);
        if (!a.endAt.isAfter(a.startAt)) throw ApiException.badRequest("bad_time", "End must be after start");
        a.reason = in.reason();
        a.colorLabel = in.colorLabel();
        a.source = source;
        a.idempotencyKey = idempotencyKey;
        a.createdBy = Claims.userId();
        save(a);
        audit.record("CREATE", "appointment", a.id, null);
        events.publishEvent(new AppointmentScheduled(a.id, a.clinicId, a.startAt));
        return dto(a);
    }

    @Transactional
    AppointmentDto setStatus(UUID id, String status) {
        if (!Appointment.STATUSES.contains(status)) throw ApiException.badRequest("bad_status", "Unknown status " + status);
        Appointment a = find(id);
        if (!a.status.equals(status)) {
            audit.record("STATUS", "appointment", a.id, Map.of("from", a.status, "to", status));
            a.status = status;
            save(a);
        }
        return dto(a);
    }

    @Transactional
    AppointmentDto reschedule(UUID id, long startAt, long endAt, Integer expectedVersion) {
        Appointment a = find(id);
        if (expectedVersion != null && expectedVersion != a.version) {
            throw ApiException.conflict("stale_version", "This appointment was changed by someone else; reload and retry");
        }
        if (endAt <= startAt) throw ApiException.badRequest("bad_time", "End must be after start");
        a.startAt = Instant.ofEpochMilli(startAt);
        a.endAt = Instant.ofEpochMilli(endAt);
        save(a);
        audit.record("RESCHEDULE", "appointment", a.id, null);
        events.publishEvent(new AppointmentScheduled(a.id, a.clinicId, a.startAt));
        return dto(a);
    }

    /** Sync push: server wins on time conflicts; status, colour and notes are last-writer-wins. */
    @Transactional
    void applyFromDevice(AppointmentDto in) {
        Appointment a = repo.findById(in.id()).orElse(null);
        if (a == null) {
            create(in, null, "app");
            return;
        }
        if (in.status() != null && !in.status().equals(a.status)) setStatus(a.id, in.status());
        a.colorLabel = in.colorLabel();
        a.reason = in.reason();
        boolean moved = in.startAt() != a.startAt.toEpochMilli() || (in.endAt() > 0 && in.endAt() != a.endAt.toEpochMilli());
        if (moved) {
            reschedule(a.id, in.startAt(), in.endAt(), null);
        } else {
            save(a);
        }
    }

    private void save(Appointment a) {
        a.updatedAt = Instant.now();
        try {
            repo.saveAndFlush(a); // flush now so constraint violations surface here, not at commit
        } catch (DataIntegrityViolationException e) {
            throw SlotTaken.translate(e);
        }
    }

    private Appointment find(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    static AppointmentDto dto(Appointment a) {
        return new AppointmentDto(a.id, a.clinicId, a.patientId, a.dentistId, a.chairId, a.procedureId,
                a.startAt.toEpochMilli(), a.endAt.toEpochMilli(), a.status, a.reason, a.colorLabel,
                a.updatedAt.toEpochMilli(), a.version);
    }

    static final class SlotTaken {
        private SlotTaken() {}

        /** SQLState 23P01 = exclusion_violation: the dentist or chair is already booked then. */
        static RuntimeException translate(DataIntegrityViolationException e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof java.sql.SQLException sql && Objects.equals(sql.getSQLState(), "23P01")) {
                    return ApiException.conflict("slot_taken", "That slot is already taken for this dentist or chair");
                }
                t = t.getCause();
            }
            return e;
        }
    }
}
