package com.dentacloud.patient;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PatientApiTest extends IntegrationTest {
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> search(String token, String q) {
        return (List<Map<String, Object>>) get("/api/v1/patients?q=" + q, token).getBody().get("items");
    }

    @Test
    void numbersAndSearchesPatients() {
        String t = token(signup("Search Dental"));
        Map<String, Object> anita = newPatient(t, "Anita Rao", "+91 98450 00001");
        newPatient(t, "Ravi Kumar", "9845000002");
        assertThat(anita).containsEntry("patientNo", "P-0001");

        assertThat(search(t, "ravi")).extracting(p -> p.get("fullName")).containsExactly("Ravi Kumar");
        assertThat(search(t, "9845000001")).extracting(p -> p.get("fullName")).containsExactly("Anita Rao");
        assertThat(search(t, "P-0002")).extracting(p -> p.get("fullName")).containsExactly("Ravi Kumar");
        assertThat(search(t, "")).hasSize(2);
    }

    @Test
    void contactAndMedicalFieldsAreEncryptedAtRest() {
        String t = token(signup("Crypto Dental"));
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "Meera Shetty", "phone", "9900112233",
                "allergies", "Penicillin", "medicalHistory", "Diabetic"));
        Map<String, Object> p = post("/api/v1/patients", body, t).getBody();
        assertThat(p).containsEntry("allergies", "Penicillin");

        // Raw read as the container superuser, past RLS, to see what is actually on disk.
        try (var c = java.sql.DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var st = c.prepareStatement("select phone_enc, allergies_enc, medical_history_enc from patient where id = ?::uuid")) {
            st.setString(1, (String) p.get("id"));
            var rs = st.executeQuery();
            rs.next();
            for (int i = 1; i <= 3; i++) {
                String raw = new String(rs.getBytes(i), StandardCharsets.ISO_8859_1);
                assertThat(raw).doesNotContain("9900112233").doesNotContain("Penicillin").doesNotContain("Diabetic");
            }
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void staleVersionIsRejected() {
        String t = token(signup("Version Dental"));
        Map<String, Object> p = newPatient(t, "Old Name", null);
        Map<String, Object> edit = new HashMap<>(p);
        edit.put("fullName", "New Name");
        ResponseEntity<Map<String, Object>> first = call(HttpMethod.PATCH, "/api/v1/patients/" + p.get("id"), edit, t, MAP);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("version")).isEqualTo(1);

        edit.put("fullName", "Lost Update"); // still carries version 0
        assertThat(call(HttpMethod.PATCH, "/api/v1/patients/" + p.get("id"), edit, t, MAP).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void otherClinicsPatientIsNotFound() {
        String a = token(signup("Own Dental"));
        String b = token(signup("Other Dental"));
        Map<String, Object> p = newPatient(a, "Private Person", null);
        assertThat(get("/api/v1/patients/" + p.get("id"), b).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(search(b, "Private")).isEmpty();
    }
}
