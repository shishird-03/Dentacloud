package com.dentacloud.identity;

import com.dentacloud.IntegrationTest;
import com.dentacloud.shared.TenantContext;
import org.junit.jupiter.api.BeforeEach;
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

/** Clinic A can never read or write clinic B data (PRD 14, 17). */
class TenantIsolationTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    Map<String, Object> a, b;

    @BeforeEach
    void twoClinics() {
        a = signup("Alpha Dental");
        b = signup("Beta Dental");
    }

    @Test
    void apiOnlyShowsOwnClinic() {
        assertThat(get("/api/v1/clinic", token(a)).getBody()).containsEntry("name", "Alpha Dental");
        assertThat(get("/api/v1/clinic", token(b)).getBody()).containsEntry("name", "Beta Dental");

        List<Map<String, Object>> aMembers = call(HttpMethod.GET, "/api/v1/clinic/members", null, token(a), LIST).getBody();
        assertThat(aMembers).extracting(m -> m.get("id")).containsExactly(a.get("memberId"));

        post("/api/v1/clinic/chairs", Map.of("name", "Alpha chair 2"), token(a));
        List<Map<String, Object>> bChairs = call(HttpMethod.GET, "/api/v1/clinic/chairs", null, token(b), LIST).getBody();
        assertThat(bChairs).extracting(c -> c.get("name")).containsExactly("Chair 1");
    }

    @Test
    void databaseRejectsCrossTenantWrites() {
        UUID aId = UUID.fromString((String) a.get("clinicId"));
        UUID bId = UUID.fromString((String) b.get("clinicId"));
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> TenantContext.runAs(aId, () -> tx.executeWithoutResult(s ->
                jdbc.update("insert into chair (id, clinic_id, name) values (?, ?, 'sneaky')", UUID.randomUUID(), bId))))
                .rootCause().hasMessageContaining("row-level security");

        Integer visible = TenantContext.callAs(aId, () -> tx.execute(s ->
                jdbc.queryForObject("select count(*) from chair where clinic_id = ?", Integer.class, bId)));
        assertThat(visible).isZero();
    }

    @Test
    void noTenantSeesNothing() {
        Integer clinics = new TransactionTemplate(txManager).execute(s ->
                jdbc.queryForObject("select count(*) from clinic", Integer.class));
        assertThat(clinics).isZero();
    }

    @Test
    void requestsNeedAValidToken() {
        assertThat(get("/api/v1/clinic", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/clinic", token(a) + "x").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
