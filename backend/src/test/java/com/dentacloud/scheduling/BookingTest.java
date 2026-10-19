package com.dentacloud.scheduling;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest extends IntegrationTest {
    private ResponseEntity<Map<String, Object>> book(String token, String key, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        if (key != null) h.set("Idempotency-Key", key);
        return http.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(body, h), MAP);
    }

    private static Map<String, Object> slot(Object patientId, Object dentistId, Instant start, int minutes) {
        Map<String, Object> m = new HashMap<>();
        m.put("patientId", patientId);
        m.put("dentistId", dentistId);
        m.put("startAt", start.toEpochMilli());
        m.put("endAt", start.plus(minutes, ChronoUnit.MINUTES).toEpochMilli());
        return m;
    }

    /** PRD 6 and 17: double-booking is impossible, even under simultaneous requests. */
    @Test
    void hundredConcurrentBookingsExactlyOneWins() throws Exception {
        Map<String, Object> owner = signup("Race Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Race Patient", null).get("id");
        Map<String, Object> body = slot(patient, owner.get("memberId"), Instant.parse("2030-01-07T04:30:00Z"), 30);

        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatus>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return HttpStatus.valueOf(book(t, UUID.randomUUID().toString(), body).getStatusCode().value());
            }));
        }
        go.countDown();
        int created = 0, taken = 0;
        for (Future<HttpStatus> f : results) {
            HttpStatus s = f.get();
            if (s == HttpStatus.CREATED) created++;
            else if (s == HttpStatus.CONFLICT) taken++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(taken).isEqualTo(99);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameBooking() {
        Map<String, Object> owner = signup("Retry Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Retry Patient", null).get("id");
        Map<String, Object> body = slot(patient, owner.get("memberId"), Instant.parse("2030-01-08T05:00:00Z"), 30);
        ResponseEntity<Map<String, Object>> first = book(t, "key-1", body);
        ResponseEntity<Map<String, Object>> retry = book(t, "key-1", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(book(t, null, body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelledSlotCanBeRebookedAndBackToBackIsFine() {
        Map<String, Object> owner = signup("Slot Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Slot Patient", null).get("id");
        Instant nine = Instant.parse("2030-01-09T03:30:00Z");
        Map<String, Object> a = book(t, "a", slot(patient, owner.get("memberId"), nine, 30)).getBody();
        assertThat(book(t, "b", slot(patient, owner.get("memberId"), nine.plus(15, ChronoUnit.MINUTES), 30))
                .getBody()).containsEntry("code", "slot_taken");
        assertThat(book(t, "c", slot(patient, owner.get("memberId"), nine.plus(30, ChronoUnit.MINUTES), 30))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        call(HttpMethod.PATCH, "/api/v1/appointments/" + a.get("id") + "/status", Map.of("status", "CANCELLED"), t, MAP);
        assertThat(book(t, "d", slot(patient, owner.get("memberId"), nine, 30)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cannotBookAnotherClinicsPatientOrDentist() {
        Map<String, Object> a = signup("Mine Dental");
        Map<String, Object> b = signup("Theirs Dental");
        Object theirPatient = newPatient(token(b), "Their Patient", null).get("id");
        Object myPatient = newPatient(token(a), "My Patient", null).get("id");
        Instant when = Instant.parse("2030-01-10T04:00:00Z");
        assertThat(book(token(a), "x", slot(theirPatient, a.get("memberId"), when, 30)).getBody())
                .containsEntry("code", "unknown_patient");
        assertThat(book(token(a), "y", slot(myPatient, b.get("memberId"), when, 30)).getBody())
                .containsEntry("code", "unknown_dentist");
    }
}
