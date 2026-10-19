package com.dentacloud.audit;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Clinical reads are audited, owners can list and verify the chain, and tampering is detected (PRD 14). */
class AuditTest extends IntegrationTest {

    @Test
    void clinicalReadsAreAuditedWithoutPatientData() {
        String t = token(signup("Audit Read Dental"));
        String id = (String) newPatient(t, "Meera Iyer", "9845000077").get("id");
        get("/api/v1/patients/" + id, t);
        call(HttpMethod.GET, "/api/v1/patients/" + id + "/visits", null, t, LIST);
        get("/api/v1/clinic", t); // not clinical: must not be audited

        List<Map<String, Object>> events = call(HttpMethod.GET, "/api/v1/audit-events", null, t, LIST).getBody();
        assertThat(events).extracting(e -> e.get("action") + " " + e.get("resourceType") + " " + e.get("resourceId"))
                .containsExactly("CREATE patient " + id, "READ patient " + id, "READ visit " + id);
        assertThat(events.get(2).get("details")).isEqualTo(Map.of("count", 0));
        assertThat(events.toString()).doesNotContain("Meera").doesNotContain("9845000077");
    }

    @Test
    void verifyDetectsAnEditedRow() throws Exception {
        String t = token(signup("Audit Chain Dental"));
        String id = (String) newPatient(t, "Chain Patient", null).get("id");
        get("/api/v1/patients/" + id, t);
        get("/api/v1/patients/" + id, t);
        assertThat(get("/api/v1/audit-events/verify", t).getBody()).containsEntry("valid", true).containsEntry("checked", 3);

        List<Map<String, Object>> events = call(HttpMethod.GET, "/api/v1/audit-events", null, t, LIST).getBody();
        long middle = ((Number) events.get(1).get("id")).longValue();
        // The app role cannot UPDATE audit_event; only a superuser (an attacker with DB access) can.
        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.prepareStatement("update audit_event set action = 'CREATE' where id = ?")) {
            st.setLong(1, middle);
            st.executeUpdate();
        }

        assertThat(get("/api/v1/audit-events/verify", t).getBody())
                .containsEntry("valid", false).containsEntry("firstBrokenId", (int) middle);
    }

    @Test
    void receptionCannotReadTheAuditLog() {
        String owner = token(signup("Audit Scope Dental"));
        List<Map<String, Object>> roles = call(HttpMethod.GET, "/api/v1/clinic/roles", null, owner, LIST).getBody();
        Object reception = roles.stream().filter(r -> "RECEPTIONIST".equals(r.get("name"))).findFirst().orElseThrow().get("id");
        String email = "desk+" + System.nanoTime() + "@test.clinic";
        post("/api/v1/clinic/members", Map.of("fullName", "Desk", "email", email, "tempPassword", "password123",
                "roleId", reception, "isDentist", false), owner);
        String desk = token(post("/api/v1/auth/login", Map.of("email", email, "password", "password123"), null).getBody());

        assertThat(call(HttpMethod.GET, "/api/v1/audit-events", null, desk, MAP).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/audit-events/verify", desk).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
