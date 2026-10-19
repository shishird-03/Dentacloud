package com.dentacloud.billing;

import com.dentacloud.billing.BillingDtos.InvoiceDto;
import com.dentacloud.billing.BillingDtos.NewInvoice;
import com.dentacloud.billing.BillingDtos.PaymentDto;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.SyncSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class InvoiceController {
    private final InvoiceService invoices;

    InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('SCOPE_invoice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceDto issue(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody NewInvoice body) {
        return invoices.issue(body, key);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('SCOPE_invoice.read')")
    InvoiceDto get(@PathVariable UUID id) {
        return invoices.get(id);
    }

    @GetMapping("/patients/{patientId}/invoices")
    @PreAuthorize("hasAuthority('SCOPE_invoice.read')")
    List<InvoiceDto> forPatient(@PathVariable UUID patientId) {
        return invoices.forPatient(patientId);
    }

    @GetMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('SCOPE_invoice.read')")
    List<PaymentDto> payments(@PathVariable UUID id) {
        return invoices.payments(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('SCOPE_invoice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentDto pay(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PaymentDto body) {
        return invoices.record(id, "PAYMENT", body, key);
    }

    @PostMapping("/invoices/{id}/refunds")
    @PreAuthorize("hasAuthority('SCOPE_invoice.refund')")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentDto refund(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PaymentDto body) {
        return invoices.record(id, "REFUND", body, key);
    }
}

/** Device-issued invoices: created once, never edited (status follows payments). */
@Component
class InvoiceSyncSource implements SyncSource {
    private final InvoiceService invoices;
    private final ObjectMapper json;
    private final Validator validator;

    InvoiceSyncSource(InvoiceService invoices, ObjectMapper json, Validator validator) {
        this.invoices = invoices;
        this.json = json;
        this.validator = validator;
    }

    @Override
    public String entity() {
        return "invoice";
    }

    @Override
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        invoices.load(ids).forEach(i -> out.put(i.id(), i));
        return out;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_invoice.write')")
    public void upsert(JsonNode payload) {
        NewInvoice in = Payloads.read(json, validator, payload, NewInvoice.class);
        if (in.id() == null) throw ApiException.badRequest("bad_payload", "id is required");
        invoices.issue(in, null);
    }
}

/** Device-recorded payments and refunds: append-only, idempotent by key. */
@Component
class PaymentSyncSource implements SyncSource {
    private final InvoiceService invoices;
    private final ObjectMapper json;
    private final Validator validator;

    PaymentSyncSource(InvoiceService invoices, ObjectMapper json, Validator validator) {
        this.invoices = invoices;
        this.json = json;
        this.validator = validator;
    }

    @Override
    public String entity() {
        return "payment";
    }

    @Override
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        invoices.loadPayments(ids).forEach(p -> out.put(p.id(), p));
        return out;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('SCOPE_invoice.write', 'SCOPE_invoice.refund')")
    public void upsert(JsonNode payload) {
        PaymentDto in = Payloads.read(json, validator, payload, PaymentDto.class);
        if (in.id() == null || in.invoiceId() == null) throw ApiException.badRequest("bad_payload", "id and invoiceId are required");
        String kind = "REFUND".equals(in.kind()) ? "REFUND" : "PAYMENT";
        invoices.record(in.invoiceId(), kind, in, in.idempotencyKey() != null ? in.idempotencyKey() : in.id().toString());
    }
}

final class Payloads {
    private Payloads() {}

    static <T> T read(ObjectMapper json, Validator validator, JsonNode payload, Class<T> type) {
        T value;
        try {
            value = json.treeToValue(payload, type);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("bad_payload", "Payload is not a valid " + type.getSimpleName());
        }
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            var v = violations.iterator().next();
            throw ApiException.badRequest("bad_payload", v.getPropertyPath() + " " + v.getMessage());
        }
        return value;
    }
}
