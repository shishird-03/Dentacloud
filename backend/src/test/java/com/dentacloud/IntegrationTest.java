package com.dentacloud;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One Postgres container for the whole suite. The API connects as the non-superuser app role,
 * exactly like production, so row-level security is really exercised (superusers bypass RLS).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
public abstract class IntegrationTest {
    protected static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/test-init.sql");

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "dentacloud_app");
        r.add("spring.datasource.password", () -> "dentacloud_app");
        r.add("spring.flyway.user", PG::getUsername);
        r.add("spring.flyway.password", PG::getPassword);
    }

    protected static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    protected static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    @Autowired protected TestRestTemplate http;

    /** Signs up a fresh clinic and returns the auth response (accessToken, clinicId, memberId, ...). */
    protected Map<String, Object> signup(String clinicName) {
        String email = "owner+" + UUID.randomUUID() + "@test.clinic";
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v1/auth/signup", HttpMethod.POST,
                new HttpEntity<>(Map.of("clinicName", clinicName, "ownerName", "Dr. " + clinicName,
                        "email", email, "password", "password123")), MAP);
        if (!r.getStatusCode().is2xxSuccessful()) throw new AssertionError("signup failed: " + r);
        Map<String, Object> body = r.getBody();
        body.put("email", email);
        return body;
    }

    protected <T> ResponseEntity<T> call(HttpMethod method, String path, Object body, String token,
                                         ParameterizedTypeReference<T> type) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, h), type);
    }

    protected ResponseEntity<Map<String, Object>> get(String path, String token) {
        return call(HttpMethod.GET, path, null, token, MAP);
    }

    protected ResponseEntity<Map<String, Object>> post(String path, Object body, String token) {
        return call(HttpMethod.POST, path, body, token, MAP);
    }

    /** Creates a patient through the API and returns it. */
    protected Map<String, Object> newPatient(String token, String name, String phone) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("fullName", name));
        if (phone != null) body.put("phone", phone);
        ResponseEntity<Map<String, Object>> r = post("/api/v1/patients", body, token);
        if (r.getStatusCode().value() != 201) throw new AssertionError("patient create failed: " + r);
        return r.getBody();
    }

    protected static String token(Map<String, Object> auth) {
        return (String) auth.get("accessToken");
    }
}
