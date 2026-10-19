package com.dentacloud.identity;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTest extends IntegrationTest {
    @Test
    void loginChecksPassword() {
        Map<String, Object> owner = signup("Gamma Dental");
        ResponseEntity<Map<String, Object>> ok = post("/api/v1/auth/login",
                Map.of("email", ((String) owner.get("email")).toUpperCase(), "password", "password123"), null);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("role", "OWNER").containsKey("accessToken");

        ResponseEntity<Map<String, Object>> bad = post("/api/v1/auth/login",
                Map.of("email", owner.get("email"), "password", "wrong-password"), null);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bad.getBody()).containsEntry("code", "bad_credentials");
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() {
        Map<String, Object> owner = signup("Delta Dental");
        String first = (String) owner.get("refreshToken");

        ResponseEntity<Map<String, Object>> rotated = post("/api/v1/auth/refresh", Map.of("refreshToken", first), null);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String second = (String) rotated.getBody().get("refreshToken");
        assertThat(second).isNotEqualTo(first);

        ResponseEntity<Map<String, Object>> reuse = post("/api/v1/auth/refresh", Map.of("refreshToken", first), null);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody()).containsEntry("code", "refresh_reused");

        // The legitimate holder of the newest token is logged out too: the chain is compromised.
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", second), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutEndsTheSession() {
        Map<String, Object> owner = signup("Epsilon Dental");
        String refresh = (String) owner.get("refreshToken");
        assertThat(post("/api/v1/auth/logout", Map.of("refreshToken", refresh), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void scopesAreEnforcedOnTheServer() {
        Map<String, Object> owner = signup("Zeta Dental");
        List<Map<String, Object>> roles = call(HttpMethod.GET, "/api/v1/clinic/roles", null, token(owner), LIST).getBody();
        Object receptionistRole = roles.stream().filter(r -> "RECEPTIONIST".equals(r.get("name"))).findFirst().orElseThrow().get("id");

        String email = "anita+" + System.nanoTime() + "@test.clinic";
        ResponseEntity<Map<String, Object>> added = post("/api/v1/clinic/members", Map.of("fullName", "Anita",
                "email", email, "tempPassword", "password123", "roleId", receptionistRole, "isDentist", false), token(owner));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String reception = token(post("/api/v1/auth/login", Map.of("email", email, "password", "password123"), null).getBody());
        assertThat(get("/api/v1/clinic", reception).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PATCH, "/api/v1/clinic", Map.of("name", "Hijacked"), reception, MAP).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/v1/clinic/chairs", Map.of("name", "X"), reception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateEmailIsRejected() {
        Map<String, Object> owner = signup("Eta Dental");
        ResponseEntity<Map<String, Object>> again = post("/api/v1/auth/signup", Map.of("clinicName", "Copy",
                "ownerName", "X", "email", owner.get("email"), "password", "password123"), null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
