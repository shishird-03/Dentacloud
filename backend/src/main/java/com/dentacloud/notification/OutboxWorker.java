package com.dentacloud.notification;

import com.dentacloud.identity.StaffDirectory;
import com.dentacloud.patient.PatientDirectory;
import com.dentacloud.scheduling.AppointmentDirectory;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains outbox_event. Claiming uses FOR UPDATE SKIP LOCKED (inside claim_outbox), so several API
 * instances can run workers without sending anything twice. Each event is handled under its own
 * clinic's tenant; failures retry with backoff, then give up after 5 attempts.
 */
@Component
class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a");

    record Event(UUID id, UUID clinicId, String topic, JsonNode payload, int attempts) {}

    private enum Outcome { DONE, SKIPPED }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final AppointmentDirectory appointments;
    private final PatientDirectory patients;
    private final StaffDirectory staff;
    private final Senders.SmsSender sms;
    private final Senders.PushSender push;
    private final boolean enabled;

    OutboxWorker(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper json,
                 AppointmentDirectory appointments, PatientDirectory patients, StaffDirectory staff,
                 Senders.SmsSender sms, Senders.PushSender push,
                 @Value("${dentacloud.outbox.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.json = json;
        this.appointments = appointments;
        this.patients = patients;
        this.staff = staff;
        this.sms = sms;
        this.push = push;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${dentacloud.outbox.poll:15s}", initialDelayString = "${dentacloud.outbox.poll:15s}")
    void poll() {
        if (enabled) runOnce();
    }

    /** Claims and handles one batch; returns how many events were processed. */
    int runOnce() {
        tx.execute(s -> jdbc.queryForObject("select release_stuck_outbox()", Integer.class));
        List<Event> batch = tx.execute(s -> jdbc.query("select * from claim_outbox(50)", (rs, i) -> {
            try {
                return new Event(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                        rs.getString("topic"), json.readTree(rs.getString("payload")), rs.getInt("attempts"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }));
        for (Event e : batch) {
            TenantContext.runAs(e.clinicId(), () -> process(e));
        }
        return batch.size();
    }

    private void process(Event e) {
        try {
            Outcome o = switch (e.topic()) {
                case Outbox.REMINDER_SMS -> reminder(e);
                case Outbox.SYNC_PUSH -> syncPing();
                default -> throw new IllegalArgumentException("unknown topic " + e.topic());
            };
            finish(e, o.name(), null, null);
        } catch (Exception ex) {
            boolean giveUp = e.attempts() >= MAX_ATTEMPTS;
            Instant retry = Instant.now().plus(Duration.ofMinutes((long) Math.pow(2, e.attempts())));
            log.warn("outbox {} {} failed (attempt {}): {}", e.topic(), e.id(), e.attempts(), ex.getClass().getSimpleName());
            finish(e, giveUp ? "FAILED" : "PENDING", ex.getClass().getSimpleName(), giveUp ? null : retry);
        }
    }

    /** Re-checks the booking at send time: cancelled, moved or opted-out reminders are skipped. */
    private Outcome reminder(Event e) throws Exception {
        UUID apptId = UUID.fromString(e.payload().path("appointmentId").asText());
        var appt = tx.execute(s -> appointments.find(apptId)).orElse(null);
        long expected = e.payload().path("startAt").asLong();
        if (appt == null || !"BOOKED".equals(appt.status()) || appt.startAt().toEpochMilli() != expected) return Outcome.SKIPPED;
        var contact = tx.execute(s -> patients.reminderContact(appt.patientId())).orElse(null);
        if (contact == null || contact.optedOut() || contact.phone() == null) return Outcome.SKIPPED;
        var clinic = tx.execute(s -> staff.currentClinic());
        String template = "appointment_reminder_" + e.payload().path("lead").asText("24h");
        String ref = sms.send(contact.phone(), template, Map.of("clinic", clinic.name(),
                "time", WHEN.withZone(clinic.zone()).format(appt.startAt())));
        tx.executeWithoutResult(s -> jdbc.update("insert into notification (id, clinic_id, patient_id, channel, template_key,"
                        + " status, provider_ref, scheduled_for) values (?, ?, ?, 'SMS', ?, 'SENT', ?, ?)",
                Ids.next(), e.clinicId(), appt.patientId(), template, ref, Timestamp.from(appt.startAt())));
        return Outcome.DONE;
    }

    /** Data-only FCM to the clinic's staff devices: "something changed, please sync". No PHI. */
    private Outcome syncPing() throws Exception {
        List<String> tokens = tx.execute(s -> jdbc.queryForList("select fcm_token from device", String.class));
        if (tokens.isEmpty()) return Outcome.SKIPPED;
        push.ping(tokens, Map.of("type", "sync"));
        return Outcome.DONE;
    }

    private void finish(Event e, String status, String error, Instant retryAt) {
        tx.executeWithoutResult(s -> jdbc.update("update outbox_event set status = ?, last_error = ?,"
                        + " run_after = coalesce(?, run_after) where id = ?",
                status, error, retryAt == null ? null : Timestamp.from(retryAt), e.id()));
    }
}
