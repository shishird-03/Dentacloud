package com.dentacloud.clinical;

import com.dentacloud.billing.BillingDtos.InvoiceDto;
import com.dentacloud.clinical.ClinicalModel.InvoicePlan;
import com.dentacloud.clinical.ClinicalModel.PlanDto;
import com.dentacloud.clinical.ClinicalModel.PlanItemPatch;
import com.dentacloud.clinical.ClinicalModel.PlanStatus;
import com.dentacloud.clinical.ClinicalModel.ToothRecordDto;
import com.dentacloud.clinical.ClinicalModel.VisitDto;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.SyncSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1")
class ClinicalController {
    private final ClinicalService clinical;

    ClinicalController(ClinicalService clinical) {
        this.clinical = clinical;
    }

    @GetMapping("/patients/{patientId}/chart")
    @PreAuthorize("hasAuthority('SCOPE_chart.read')")
    List<ToothRecordDto> chart(@PathVariable UUID patientId) {
        return clinical.chartOf(patientId);
    }

    @PostMapping("/patients/{patientId}/chart/records")
    @PreAuthorize("hasAuthority('SCOPE_chart.write')")
    @ResponseStatus(HttpStatus.CREATED)
    List<ToothRecordDto> addRecords(@PathVariable UUID patientId, @RequestBody List<@Valid ToothRecordDto> records) {
        return clinical.addRecords(patientId, records);
    }

    @GetMapping("/patients/{patientId}/visits")
    @PreAuthorize("hasAuthority('SCOPE_chart.read')")
    List<VisitDto> visits(@PathVariable UUID patientId) {
        return clinical.visitsOf(patientId);
    }

    @PostMapping("/visits")
    @PreAuthorize("hasAuthority('SCOPE_chart.write')")
    @ResponseStatus(HttpStatus.CREATED)
    VisitDto saveVisit(@Valid @RequestBody VisitDto body) {
        return clinical.saveVisit(body);
    }

    @GetMapping("/visits/{id}")
    @PreAuthorize("hasAuthority('SCOPE_chart.read')")
    VisitDto visit(@PathVariable UUID id) {
        return clinical.visit(id);
    }

    @GetMapping("/patients/{patientId}/treatment-plans")
    @PreAuthorize("hasAuthority('SCOPE_plan.read')")
    List<PlanDto> plans(@PathVariable UUID patientId) {
        return clinical.plansOf(patientId);
    }

    @PostMapping("/treatment-plans")
    @PreAuthorize("hasAuthority('SCOPE_plan.write')")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDto savePlan(@Valid @RequestBody PlanDto body) {
        return clinical.savePlan(body);
    }

    @PatchMapping("/treatment-plans/{id}")
    @PreAuthorize("hasAuthority('SCOPE_plan.write')")
    PlanDto planStatus(@PathVariable UUID id, @Valid @RequestBody PlanStatus body) {
        return clinical.updatePlanStatus(id, body.status());
    }

    @PatchMapping("/plan-items/{id}")
    @PreAuthorize("hasAuthority('SCOPE_plan.write')")
    PlanDto planItem(@PathVariable UUID id, @Valid @RequestBody PlanItemPatch body) {
        return clinical.updateItem(id, body.status(), body.doneVisitId());
    }

    @PostMapping("/treatment-plans/{id}/invoice")
    @PreAuthorize("hasAuthority('SCOPE_invoice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceDto invoicePlan(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody InvoicePlan body) {
        return clinical.invoicePlan(id, body, key);
    }
}

/** Sync for visits (last-writer-wins), chart records (append-only) and plans (with their items). */
@Component
class ClinicalSync {
    private final ClinicalService clinical;
    private final ObjectMapper json;

    ClinicalSync(ClinicalService clinical, ObjectMapper json) {
        this.clinical = clinical;
        this.json = json;
    }

    <T> T read(JsonNode payload, Class<T> type) {
        try {
            return json.treeToValue(payload, type);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("bad_payload", "Payload is not a valid " + type.getSimpleName());
        }
    }

    static <T> Map<UUID, Object> byId(List<T> list, Function<T, UUID> id) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        list.forEach(x -> out.put(id.apply(x), x));
        return out;
    }

    @Component
    static class Visits implements SyncSource {
        private final ClinicalSync s;
        Visits(ClinicalSync s) { this.s = s; }
        @Override public String entity() { return "visit"; }
        @Override public Map<UUID, Object> load(Collection<UUID> ids) { return byId(s.clinical.loadVisits(ids), VisitDto::id); }
        @Override @PreAuthorize("hasAuthority('SCOPE_chart.write')")
        public void upsert(JsonNode payload) {
            VisitDto v = s.read(payload, VisitDto.class);
            if (v.id() == null || v.patientId() == null) throw ApiException.badRequest("bad_payload", "id and patientId are required");
            s.clinical.saveVisit(v);
        }
    }

    @Component
    static class Chart implements SyncSource {
        private final ClinicalSync s;
        Chart(ClinicalSync s) { this.s = s; }
        @Override public String entity() { return "tooth_record"; }
        @Override public Map<UUID, Object> load(Collection<UUID> ids) { return byId(s.clinical.loadRecords(ids), ToothRecordDto::id); }
        @Override @PreAuthorize("hasAuthority('SCOPE_chart.write')")
        public void upsert(JsonNode payload) {
            ToothRecordDto r = s.read(payload, ToothRecordDto.class);
            if (r.id() == null || r.patientId() == null) throw ApiException.badRequest("bad_payload", "id and patientId are required");
            s.clinical.addRecords(r.patientId(), List.of(r));
        }
    }

    @Component
    static class Plans implements SyncSource {
        private final ClinicalSync s;
        Plans(ClinicalSync s) { this.s = s; }
        @Override public String entity() { return "treatment_plan"; }
        @Override public Map<UUID, Object> load(Collection<UUID> ids) { return byId(s.clinical.loadPlans(ids), PlanDto::id); }
        @Override @PreAuthorize("hasAuthority('SCOPE_plan.write')")
        public void upsert(JsonNode payload) {
            PlanDto p = s.read(payload, PlanDto.class);
            if (p.id() == null || p.patientId() == null) throw ApiException.badRequest("bad_payload", "id and patientId are required");
            s.clinical.savePlan(p);
        }
    }
}
