package com.dentacloud.clinical;

import com.dentacloud.IntegrationTest;
import com.dentacloud.shared.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void chartIsAppendOnlyHistory() {
        Map<String, Object> owner = signup("Chart Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Chart Patient", null).get("id");
        String path = "/api/v1/patients/" + patient + "/chart/records";
        String id = UUID.randomUUID().toString();

        List<Map<String, Object>> first = call(HttpMethod.POST, path, List.of(
                Map.of("id", id, "toothFdi", 36, "surface", "O", "condition", "CARIES"),
                Map.of("toothFdi", 55, "condition", "SOUND")), t, LIST).getBody();
        assertThat(first).hasSize(2);
        // A retried upload with the same id adds nothing.
        assertThat(call(HttpMethod.POST, path, List.of(Map.of("id", id, "toothFdi", 36, "surface", "O", "condition", "CARIES")),
                t, LIST).getBody()).isEmpty();
        call(HttpMethod.POST, path, List.of(Map.of("toothFdi", 36, "surface", "O", "condition", "FILLED")), t, LIST);

        List<Map<String, Object>> chart = call(HttpMethod.GET, "/api/v1/patients/" + patient + "/chart", null, t, LIST).getBody();
        assertThat(chart).extracting(r -> r.get("condition")).containsExactly("CARIES", "SOUND", "FILLED");

        assertThat(call(HttpMethod.POST, path, List.of(Map.of("toothFdi", 19, "condition", "CARIES")), t, MAP).getBody())
                .containsEntry("code", "bad_tooth");

        UUID clinic = UUID.fromString((String) owner.get("clinicId"));
        assertThatThrownBy(() -> TenantContext.runAs(clinic, () -> new TransactionTemplate(txManager).executeWithoutResult(s ->
                jdbc.update("update tooth_record set condition = 'SOUND'"))))
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void visitNotesAreEncryptedAndPrescriptionsNeedADentist() {
        Map<String, Object> owner = signup("Visit Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Visit Patient", null).get("id");
        Map<String, Object> visit = post("/api/v1/visits", Map.of("patientId", patient, "complaint", "Pain lower left",
                "diagnosis", "Irreversible pulpitis 36", "notes", "Advised RCT"), t).getBody();
        assertThat(visit).containsEntry("diagnosis", "Irreversible pulpitis 36");

        try (var c = java.sql.DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.prepareStatement("select diagnosis_enc from visit where id = ?::uuid")) {
            st.setString(1, (String) visit.get("id"));
            var rs = st.executeQuery();
            rs.next();
            assertThat(new String(rs.getBytes(1), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("pulpitis");
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }

        Map<String, Object> rx = post("/api/v1/prescriptions", Map.of("patientId", patient, "visitId", visit.get("id"),
                "items", List.of(Map.of("drugName", "Amoxicillin 500 mg", "dose", "1 cap", "frequency", "TDS", "durationDays", 5))), t).getBody();
        assertThat(rx).containsKey("id");
        assertThat(call(HttpMethod.GET, "/api/v1/patients/" + patient + "/prescriptions", null, t, LIST).getBody()).hasSize(1);

        String other = token(signup("Other Visit Dental"));
        assertThat(get("/api/v1/visits/" + visit.get("id"), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
