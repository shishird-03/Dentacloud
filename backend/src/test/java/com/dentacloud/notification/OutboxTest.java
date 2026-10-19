package com.dentacloud.notification;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTest extends IntegrationTest {
    @Autowired OutboxWorker worker;

    private Map<String, Object> book(Map<String, Object> owner, Object patient, Instant start) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token(owner));
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, Object> body = new HashMap<>(Map.of("patientId", patient, "dentistId", owner.get("memberId"),
                "startAt", start.toEpochMilli(), "endAt", start.plus(30, ChronoUnit.MINUTES).toEpochMilli()));
        return http.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(body, h), MAP).getBody();
    }

    /** Raw access as the superuser: the test plays "time passes" by making events due now. */
    private static int sql(String update, Object... args) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.prepareStatement(update)) {
            for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
            return st.executeUpdate();
        }
    }

    private static int count(String query, Object... args) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.prepareStatement(query)) {
            for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
            ResultSet rs = st.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void bookingSchedulesTwoRemindersThatAreSentOnceAcrossClinics() throws Exception {
        Instant inThreeDays = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> a = signup("Remind A Dental");
        Map<String, Object> b = signup("Remind B Dental");
        Map<String, Object> apptA = book(a, newPatient(token(a), "Remind A", "9800011111").get("id"), inThreeDays);
        book(b, newPatient(token(b), "Remind B", "9800022222").get("id"), inThreeDays);

        UUID clinicA = UUID.fromString((String) a.get("clinicId"));
        assertThat(count("select count(*) from outbox_event where clinic_id = ? and topic = 'reminder.sms'", clinicA)).isEqualTo(2);

        sql("update outbox_event set run_after = now() where clinic_id in (?, ?)", clinicA, UUID.fromString((String) b.get("clinicId")));
        while (worker.runOnce() > 0) { /* drain */ }

        assertThat(count("select count(*) from notification where clinic_id = ? and channel = 'SMS' and status = 'SENT'", clinicA)).isEqualTo(2);
        assertThat(count("select count(*) from notification where channel = 'SMS' and clinic_id = ?",
                UUID.fromString((String) b.get("clinicId")))).isEqualTo(2);
        assertThat(worker.runOnce()).as("nothing is sent twice").isZero();

        // Push pings have no registered devices yet, so they are skipped rather than failed.
        assertThat(count("select count(*) from outbox_event where clinic_id = ? and topic = 'push.sync' and status = 'SKIPPED'", clinicA)).isEqualTo(1);
        assertThat(count("select count(*) from outbox_event where clinic_id = ? and status = 'FAILED'", clinicA)).isZero();
        assertThat(apptA).containsKey("id");
    }

    @Test
    void cancelledOrMovedAppointmentsAreNotReminded() throws Exception {
        Map<String, Object> owner = signup("Skip Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Skip Patient", "9800033333").get("id");
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> appt = book(owner, patient, start);
        call(HttpMethod.PATCH, "/api/v1/appointments/" + appt.get("id") + "/status", Map.of("status", "CANCELLED"), t, MAP);

        UUID clinic = UUID.fromString((String) owner.get("clinicId"));
        sql("update outbox_event set run_after = now() where clinic_id = ?", clinic);
        while (worker.runOnce() > 0) { /* drain */ }
        assertThat(count("select count(*) from notification where clinic_id = ?", clinic)).isZero();
        assertThat(count("select count(*) from outbox_event where clinic_id = ? and topic = 'reminder.sms' and status = 'SKIPPED'", clinic)).isEqualTo(2);
    }

    @Test
    void dashboardReportsUseTheLedger() {
        Map<String, Object> owner = signup("Report Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Report Patient", null).get("id");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(t);
        h.set("Idempotency-Key", "inv");
        Map<String, Object> inv = http.exchange("/api/v1/invoices", HttpMethod.POST, new HttpEntity<>(Map.of("patientId", patient,
                "discountPaise", 0, "lines", java.util.List.of(Map.of("description", "X", "qty", 1, "amountPaise", 1_000_00,
                        "taxBasisPoints", 0))), h), MAP).getBody();
        h.set("Idempotency-Key", "pay");
        http.exchange("/api/v1/invoices/" + inv.get("id") + "/payments", HttpMethod.POST,
                new HttpEntity<>(Map.of("mode", "CASH", "amountPaise", 400_00), h), MAP);

        assertThat(get("/api/v1/reports/daily-collection", t).getBody()).containsEntry("collectedPaise", 400_00);
        assertThat(get("/api/v1/reports/dues", t).getBody()).containsEntry("duePaise", 600_00).containsEntry("openInvoices", 1);
    }
}
