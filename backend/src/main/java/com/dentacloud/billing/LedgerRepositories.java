package com.dentacloud.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceRepository extends JpaRepository<Ledger.Invoice, UUID> {
    Optional<Ledger.Invoice> findByIdempotencyKey(String key);

    List<Ledger.Invoice> findByPatientIdOrderByIssuedAtDesc(UUID patientId);
}

interface LineRepository extends JpaRepository<Ledger.Line, UUID> {
    List<Ledger.Line> findByInvoiceIdIn(java.util.Collection<UUID> invoiceIds);

    boolean existsByPlanItemId(UUID planItemId);
}

interface PaymentRepository extends JpaRepository<Ledger.Payment, UUID> {
    List<Ledger.Payment> findByInvoiceIdOrderByRecordedAt(UUID invoiceId);

    Optional<Ledger.Payment> findByIdempotencyKey(String key);
}
