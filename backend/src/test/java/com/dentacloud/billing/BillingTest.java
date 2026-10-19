package com.dentacloud.billing;

import com.dentacloud.IntegrationTest;
import com.dentacloud.shared.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private ResponseEntity<Map<String, Object>> send(String path, String key, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        if (key != null) h.set("Idempotency-Key", key);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), MAP);
    }

    private Map<String, Object> invoice(String token, Object patientId) {
        return send("/api/v1/invoices", UUID.randomUUID().toString(), Map.of("patientId", patientId, "discountPaise", 100_00,
                "lines", List.of(
                        Map.of("description", "Scaling", "qty", 1, "amountPaise", 1_200_00, "taxBasisPoints", 0),
                        Map.of("description", "Whitening kit", "qty", 2, "amountPaise", 500_00, "taxBasisPoints", 1800))),
                token).getBody();
    }

    private static Map<String, Object> pay(long paise) {
        return Map.of("mode", "UPI", "amountPaise", paise);
    }

    @Test
    void totalsAreComputedOnTheServerAndStatusFollowsTheLedger() {
        Map<String, Object> owner = signup("Ledger Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Bill Patient", null).get("id");
        Map<String, Object> inv = invoice(t, patient);
        // 1200 + 2 x 500 = 2200 subtotal, tax 18% of 1000 = 180, minus 100 discount
        assertThat(inv).containsEntry("subtotalPaise", 2_200_00).containsEntry("taxPaise", 180_00)
                .containsEntry("totalPaise", 2_280_00).containsEntry("invoiceNo", "INV-00001").containsEntry("status", "ISSUED");
        String base = "/api/v1/invoices/" + inv.get("id");

        assertThat(send(base + "/payments", "p1", pay(1_000_00), t).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(base + "/payments", "p1", pay(1_000_00), t).getStatusCode())
                .as("same key is the same payment").isEqualTo(HttpStatus.CREATED);
        assertThat(get(base, t).getBody()).containsEntry("status", "PARTIAL").containsEntry("paidPaise", 1_000_00);

        assertThat(send(base + "/payments", "p2", pay(5_000_00), t).getBody()).containsEntry("code", "overpayment");
        send(base + "/payments", "p3", pay(1_280_00), t);
        assertThat(get(base, t).getBody()).containsEntry("status", "PAID");

        assertThat(send(base + "/refunds", "r1", pay(9_999_00), t).getBody()).containsEntry("code", "refund_exceeds_paid");
        assertThat(send(base + "/refunds", "r2", pay(280_00), t).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get(base, t).getBody()).containsEntry("status", "PARTIAL").containsEntry("paidPaise", 2_000_00);
    }

    @Test
    void paymentsCannotBeEditedOrDeletedEvenBypassingTheApi() {
        Map<String, Object> owner = signup("Immutable Dental");
        String t = token(owner);
        Map<String, Object> inv = invoice(t, newPatient(t, "Imm Patient", null).get("id"));
        send("/api/v1/invoices/" + inv.get("id") + "/payments", "k", pay(500_00), t);

        UUID clinic = UUID.fromString((String) owner.get("clinicId"));
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> TenantContext.runAs(clinic, () -> tx.executeWithoutResult(s ->
                jdbc.update("update payment_entry set amount_paise = 1"))))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.runAs(clinic, () -> tx.executeWithoutResult(s ->
                jdbc.update("delete from payment_entry"))))
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void receptionCanTakePaymentsButNotRefund() {
        Map<String, Object> owner = signup("Refund Dental");
        String t = token(owner);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = call(HttpMethod.GET, "/api/v1/clinic/roles", null, t, LIST).getBody();
        Object reception = roles.stream().filter(r -> "RECEPTIONIST".equals(r.get("name"))).findFirst().orElseThrow().get("id");
        String email = "desk+" + System.nanoTime() + "@test.clinic";
        post("/api/v1/clinic/members", Map.of("fullName", "Desk", "email", email, "tempPassword", "password123",
                "roleId", reception, "isDentist", false), t);
        String desk = token(post("/api/v1/auth/login", Map.of("email", email, "password", "password123"), null).getBody());

        Map<String, Object> inv = invoice(desk, newPatient(desk, "Desk Patient", null).get("id"));
        String base = "/api/v1/invoices/" + inv.get("id");
        assertThat(send(base + "/payments", "a", pay(100_00), desk).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(base + "/refunds", "b", pay(100_00), desk).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void planItemsAreBilledOnce() {
        Map<String, Object> owner = signup("Plan Dental");
        String t = token(owner);
        Object patient = newPatient(t, "Plan Patient", null).get("id");
        List<Map<String, Object>> procs = call(HttpMethod.GET, "/api/v1/procedures", null, t, LIST).getBody();
        Object rct = procs.stream().filter(p -> "RCT".equals(p.get("code"))).findFirst().orElseThrow().get("id");
        Object crown = procs.stream().filter(p -> "CRWN".equals(p.get("code"))).findFirst().orElseThrow().get("id");

        Map<String, Object> plan = post("/api/v1/treatment-plans", Map.of("patientId", patient, "title", "Lower left molar",
                "items", List.of(Map.of("procedureId", rct, "toothFdi", 36, "estPaise", 6_000_00),
                        Map.of("procedureId", crown, "toothFdi", 36, "estPaise", 8_000_00))), t).getBody();
        assertThat(plan).containsEntry("estimatePaise", 14_000_00);
        List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");

        String path = "/api/v1/treatment-plans/" + plan.get("id") + "/invoice";
        Map<String, Object> inv = send(path, "plan-1", Map.of("itemIds", List.of(items.get(0).get("id")), "discountPaise", 0), t).getBody();
        assertThat(inv).containsEntry("totalPaise", 6_000_00);
        assertThat(((List<Map<String, Object>>) inv.get("lines")).get(0).get("description")).isEqualTo("Root canal treatment (tooth 36)");

        assertThat(send(path, "plan-2", Map.of("itemIds", List.of(items.get(0).get("id")), "discountPaise", 0), t).getBody())
                .containsEntry("code", "already_billed");
    }
}
