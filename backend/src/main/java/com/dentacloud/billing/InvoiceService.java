package com.dentacloud.billing;

import com.dentacloud.audit.AuditService;
import com.dentacloud.billing.BillingDtos.InvoiceDto;
import com.dentacloud.billing.BillingDtos.InvoiceLineDto;
import com.dentacloud.billing.BillingDtos.NewInvoice;
import com.dentacloud.billing.BillingDtos.PaymentDto;
import com.dentacloud.patient.PatientDirectory;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Claims;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Invoices and the payment ledger (F8). Money moves only by appending PAYMENT or REFUND entries;
 * an invoice's status is derived from them. Other modules bill through this public service.
 */
@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final LineRepository lines;
    private final PaymentRepository payments;
    private final PatientDirectory patients;
    private final AuditService audit;
    private final EntityManager em;

    InvoiceService(InvoiceRepository invoices, LineRepository lines, PaymentRepository payments,
                   PatientDirectory patients, AuditService audit, EntityManager em) {
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.patients = patients;
        this.audit = audit;
        this.em = em;
    }

    /** Issues an invoice; the same idempotency key or client id returns the original. Totals are computed here. */
    @Transactional
    public InvoiceDto issue(NewInvoice req, String idempotencyKey) {
        var existing = idempotencyKey != null ? invoices.findByIdempotencyKey(idempotencyKey)
                : req.id() == null ? java.util.Optional.<Ledger.Invoice>empty() : invoices.findById(req.id());
        if (existing.isPresent()) return dto(existing.get());
        if (!patients.exists(req.patientId())) throw ApiException.badRequest("unknown_patient", "Unknown patient");
        for (InvoiceLineDto l : req.lines()) {
            if (l.planItemId() != null && lines.existsByPlanItemId(l.planItemId())) {
                throw ApiException.conflict("already_billed", "A treatment plan item on this invoice was already billed");
            }
        }
        BillMath.Totals t;
        try {
            t = BillMath.compute(req.lines().stream()
                    .map(l -> new BillMath.Line(l.amountPaise(), l.qty(), l.taxBasisPoints())).toList(), req.discountPaise());
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw ApiException.badRequest("bad_amounts", e.getMessage());
        }
        Ledger.Invoice inv = new Ledger.Invoice();
        inv.id = req.id() == null ? Ids.next() : req.id();
        inv.clinicId = TenantContext.require();
        inv.patientId = req.patientId();
        inv.invoiceNo = nextInvoiceNo();
        inv.subtotalPaise = t.subtotalPaise();
        inv.discountPaise = t.discountPaise();
        inv.taxPaise = t.taxPaise();
        inv.totalPaise = t.totalPaise();
        inv.idempotencyKey = idempotencyKey;
        invoices.save(inv);
        for (InvoiceLineDto l : req.lines()) {
            Ledger.Line line = new Ledger.Line();
            line.id = l.id() == null ? Ids.next() : l.id();
            line.clinicId = inv.clinicId;
            line.invoiceId = inv.id;
            line.planItemId = l.planItemId();
            line.description = l.description();
            line.qty = l.qty();
            line.amountPaise = l.amountPaise();
            line.taxBasisPoints = l.taxBasisPoints();
            lines.save(line);
        }
        invoices.flush();
        audit.record("ISSUE", "invoice", inv.id, Map.of("totalPaise", inv.totalPaise, "lines", req.lines().size()));
        return dto(inv);
    }

    /** Appends a payment or refund. Payments can't exceed the balance; refunds can't exceed what was paid. */
    @Transactional
    public PaymentDto record(UUID invoiceId, String kind, PaymentDto req, String idempotencyKey) {
        String key = idempotencyKey != null ? idempotencyKey : req.idempotencyKey();
        if (key == null || key.isBlank()) throw ApiException.badRequest("idempotency_key_required", "Idempotency-Key is required");
        var existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) return dto(existing.get());

        // Lock the invoice row so two tills can't both take the last rupee of the balance.
        Ledger.Invoice inv = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        em.lock(inv, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if ("VOID".equals(inv.status)) throw ApiException.conflict("invoice_void", "This invoice was voided");
        long paid = paid(inv.id);
        if ("PAYMENT".equals(kind) && req.amountPaise() > inv.totalPaise - paid) {
            throw ApiException.conflict("overpayment", "Amount is more than the balance due");
        }
        if ("REFUND".equals(kind) && req.amountPaise() > paid) {
            throw ApiException.conflict("refund_exceeds_paid", "Refund is more than what was paid");
        }
        Ledger.Payment p = new Ledger.Payment();
        p.id = req.id() == null ? Ids.next() : req.id();
        p.clinicId = inv.clinicId;
        p.invoiceId = inv.id;
        p.kind = kind;
        p.mode = req.mode();
        p.amountPaise = req.amountPaise();
        p.reference = req.reference();
        p.idempotencyKey = key;
        p.recordedBy = Claims.memberId();
        payments.save(p);

        long nowPaid = paid + ("PAYMENT".equals(kind) ? p.amountPaise : -p.amountPaise);
        inv.status = nowPaid <= 0 ? "ISSUED" : nowPaid >= inv.totalPaise ? "PAID" : "PARTIAL";
        invoices.flush();
        audit.record(kind, "invoice", inv.id, Map.of("amountPaise", p.amountPaise, "mode", p.mode));
        return dto(p);
    }

    @Transactional(readOnly = true)
    public InvoiceDto get(UUID id) {
        return dto(invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice")));
    }

    @Transactional(readOnly = true)
    List<InvoiceDto> forPatient(UUID patientId) {
        return invoices.findByPatientIdOrderByIssuedAtDesc(patientId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    List<PaymentDto> payments(UUID invoiceId) {
        return payments.findByInvoiceIdOrderByRecordedAt(invoiceId).stream().map(InvoiceService::dto).toList();
    }

    @Transactional(readOnly = true)
    List<InvoiceDto> load(Collection<UUID> ids) {
        return invoices.findAllById(ids).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    List<PaymentDto> loadPayments(Collection<UUID> ids) {
        return payments.findAllById(ids).stream().map(InvoiceService::dto).toList();
    }

    private long paid(UUID invoiceId) {
        return payments.findByInvoiceIdOrderByRecordedAt(invoiceId).stream()
                .mapToLong(p -> "PAYMENT".equals(p.kind) ? p.amountPaise : -p.amountPaise).sum();
    }

    private String nextInvoiceNo() {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtext('invoice_no:' || app_clinic()::text))").getSingleResult();
        Number max = (Number) em.createNativeQuery(
                "select coalesce(max(cast(substring(invoice_no from 5) as int)), 0) from invoice where invoice_no ~ '^INV-[0-9]+$'")
                .getSingleResult();
        return String.format(Locale.ROOT, "INV-%05d", max.intValue() + 1);
    }

    private InvoiceDto dto(Ledger.Invoice i) {
        Map<UUID, List<Ledger.Line>> byInvoice = lines.findByInvoiceIdIn(List.of(i.id)).stream()
                .collect(Collectors.groupingBy(l -> l.invoiceId));
        List<InvoiceLineDto> ls = byInvoice.getOrDefault(i.id, List.of()).stream()
                .map(l -> new InvoiceLineDto(l.id, l.invoiceId, l.planItemId, l.description, l.qty, l.amountPaise, l.taxBasisPoints))
                .toList();
        return new InvoiceDto(i.id, i.clinicId, i.patientId, i.invoiceNo, i.subtotalPaise, i.discountPaise, i.taxPaise,
                i.totalPaise, i.status, i.issuedAt.toEpochMilli(), paid(i.id), ls);
    }

    private static PaymentDto dto(Ledger.Payment p) {
        return new PaymentDto(p.id, p.clinicId, p.invoiceId, p.kind, p.mode, p.amountPaise, p.reference,
                p.idempotencyKey, p.recordedBy, p.recordedAt.toEpochMilli());
    }
}
