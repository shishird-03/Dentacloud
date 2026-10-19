package com.dentacloud.billing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Billing tables. Payments are append-only: the app role has no UPDATE or DELETE on payment_entry. */
final class Ledger {
    private Ledger() {}

    @Entity
    @Table(name = "invoice")
    static class Invoice {
        @Id UUID id;
        UUID clinicId;
        UUID patientId;
        String invoiceNo;
        long subtotalPaise;
        long discountPaise;
        long taxPaise;
        long totalPaise;
        String status = "ISSUED";
        Instant issuedAt = Instant.now();
        String idempotencyKey;
    }

    @Entity
    @Table(name = "invoice_line")
    static class Line {
        @Id UUID id;
        UUID clinicId;
        UUID invoiceId;
        UUID planItemId;
        String description;
        int qty = 1;
        long amountPaise;
        int taxBasisPoints;
    }

    @Entity
    @Table(name = "payment_entry")
    static class Payment {
        @Id UUID id;
        UUID clinicId;
        UUID invoiceId;
        String kind;
        String mode;
        long amountPaise;
        String reference;
        String idempotencyKey;
        UUID recordedBy;
        Instant recordedAt = Instant.now();
    }
}
