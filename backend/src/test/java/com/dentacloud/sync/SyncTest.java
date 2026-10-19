package com.dentacloud.sync;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SyncTest extends IntegrationTest {
    private static Map<String, Object> op(String key, String entity, String id, Map<String, Object> payload) {
        return Map.of("idempotencyKey", key, "entity", entity, "entityId", id, "operation", "UPSERT", "payload", payload);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> push(String token, List<Map<String, Object>> ops) {
        return (List<Map<String, Object>>) post("/api/v1/sync/push", Map.of("ops", ops), token).getBody().get("results");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pull(String token, long since) {
        return (List<Map<String, Object>>) get("/api/v1/sync/pull?since=" + since, token).getBody().get("changes");
    }

    @Test
    void offlineCreatedRowsPushOnceAndPullBack() {
        String t = token(signup("Sync Dental"));
        String patientId = UUID.randomUUID().toString();
        Map<String, Object> patient = Map.of("id", patientId, "fullName", "Offline Patient", "phone", "9800000001",
                "patientNo", "P-0042", "photoPath", "/data/local.jpg");
        List<Map<String, Object>> r1 = push(t, List.of(op("k1", "patient", patientId, patient)));
        List<Map<String, Object>> r2 = push(t, List.of(op("k1", "patient", patientId, patient)));
        assertThat(r1.get(0)).containsEntry("status", "APPLIED");
        assertThat(r2).isEqualTo(r1);

        List<Map<String, Object>> changes = pull(t, 0);
        Map<String, Object> pulled = changes.stream().filter(c -> c.get("id").equals(patientId)).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) pulled.get("data");
        assertThat(data).containsEntry("fullName", "Offline Patient").containsEntry("patientNo", "P-0001");
        // The seeded price list syncs down too.
        assertThat(changes).anyMatch(c -> "procedure".equals(c.get("entity")));
    }

    @Test
    void slotConflictIsReportedPerOpWithoutFailingTheBatch() {
        Map<String, Object> owner = signup("Conflict Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Conflict Patient", null).get("id");
        long start = Instant.parse("2030-02-01T04:00:00Z").toEpochMilli();
        String a1 = UUID.randomUUID().toString(), a2 = UUID.randomUUID().toString();
        Map<String, Object> first = Map.of("id", a1, "patientId", patient, "dentistId", owner.get("memberId"),
                "startAt", start, "endAt", start + 1_800_000, "status", "BOOKED");
        Map<String, Object> clash = Map.of("id", a2, "patientId", patient, "dentistId", owner.get("memberId"),
                "startAt", start + 600_000, "endAt", start + 2_400_000, "status", "BOOKED");

        List<Map<String, Object>> r = push(t, List.of(op("a1", "appointment", a1, first), op("a2", "appointment", a2, clash),
                op("p1", "procedure", UUID.randomUUID().toString(), Map.of())));
        assertThat(r).extracting(x -> x.get("status")).containsExactly("APPLIED", "CONFLICT", "REJECTED");
        assertThat(r.get(1)).containsEntry("code", "slot_taken");
    }

    @Test
    void pullNeverLeaksAnotherClinic() {
        String a = token(signup("Pull A Dental"));
        String b = token(signup("Pull B Dental"));
        newPatient(a, "Only In A", null);
        assertThat(pull(b, 0)).noneMatch(c -> "patient".equals(c.get("entity")));
    }
}
