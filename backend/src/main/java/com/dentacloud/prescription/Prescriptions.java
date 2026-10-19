package com.dentacloud.prescription;

import com.dentacloud.audit.AuditService;
import com.dentacloud.identity.StaffDirectory;
import com.dentacloud.patient.PatientDirectory;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Claims;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.SyncSource;
import com.dentacloud.shared.TenantContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prescriptions (F9). Issued once, never edited; the PDF with clinic letterhead is drawn on the device
 * and shared through the Android share sheet.
 */
@RestController
@RequestMapping("/api/v1")
class Prescriptions implements SyncSource {
    @Entity
    @Table(name = "prescription")
    static class Rx {
        @Id UUID id;
        UUID clinicId;
        UUID patientId;
        UUID visitId;
        UUID dentistId;
        Instant issuedAt = Instant.now();
        String notes;
    }

    @Entity
    @Table(name = "prescription_item")
    static class RxItem {
        @Id UUID id;
        UUID clinicId;
        UUID prescriptionId;
        String drugName;
        String dose;
        String frequency;
        Integer durationDays;
        String instructions;
    }

    interface RxRepository extends JpaRepository<Rx, UUID> {
        List<Rx> findByPatientIdOrderByIssuedAtDesc(UUID patientId);
    }

    interface RxItemRepository extends JpaRepository<RxItem, UUID> {
        List<RxItem> findByPrescriptionIdIn(Collection<UUID> ids);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemDto(UUID id, UUID prescriptionId, @NotBlank @Size(max = 120) String drugName, @Size(max = 60) String dose,
                   @Size(max = 60) String frequency, @PositiveOrZero Integer durationDays, @Size(max = 300) String instructions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RxDto(UUID id, UUID clinicId, @NotNull UUID patientId, UUID visitId, UUID dentistId, long issuedAt,
                 @Size(max = 1000) String notes, @NotEmpty List<@Valid ItemDto> items) {}

    private final RxService service;
    private final ObjectMapper json;

    Prescriptions(RxService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAuthority('SCOPE_prescription.write')")
    @ResponseStatus(HttpStatus.CREATED)
    RxDto issue(@Valid @RequestBody RxDto body) {
        return service.issue(body);
    }

    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('SCOPE_prescription.read')")
    RxDto get(@PathVariable UUID id) {
        return service.load(List.of(id)).stream().findFirst().orElseThrow(() -> ApiException.notFound("Prescription"));
    }

    @GetMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('SCOPE_prescription.read')")
    List<RxDto> forPatient(@PathVariable UUID patientId) {
        return service.forPatient(patientId);
    }

    @Override
    public String entity() {
        return "prescription";
    }

    @Override
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        service.load(ids).forEach(r -> out.put(r.id(), r));
        return out;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_prescription.write')")
    public void upsert(JsonNode payload) {
        RxDto in;
        try {
            in = json.treeToValue(payload, RxDto.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("bad_payload", "Not a valid prescription");
        }
        if (in.id() == null || in.patientId() == null || in.items() == null || in.items().isEmpty()) {
            throw ApiException.badRequest("bad_payload", "id, patientId and at least one item are required");
        }
        service.issue(in);
    }

    @Service
    static class RxService {
        private final RxRepository rxs;
        private final RxItemRepository items;
        private final PatientDirectory patients;
        private final StaffDirectory staff;
        private final AuditService audit;

        RxService(RxRepository rxs, RxItemRepository items, PatientDirectory patients, StaffDirectory staff, AuditService audit) {
            this.rxs = rxs;
            this.items = items;
            this.patients = patients;
            this.staff = staff;
            this.audit = audit;
        }

        @Transactional
        RxDto issue(RxDto in) {
            if (in.id() != null && rxs.existsById(in.id())) return load(List.of(in.id())).get(0); // retried
            if (!patients.exists(in.patientId())) throw ApiException.badRequest("unknown_patient", "Unknown patient");
            Rx rx = new Rx();
            rx.id = in.id() == null ? Ids.next() : in.id();
            rx.clinicId = TenantContext.require();
            rx.patientId = in.patientId();
            rx.visitId = in.visitId();
            rx.dentistId = in.dentistId() == null ? Claims.memberId() : in.dentistId();
            if (!staff.isActiveDentist(rx.dentistId)) throw ApiException.badRequest("unknown_dentist", "Only a dentist can prescribe");
            if (in.issuedAt() > 0) rx.issuedAt = Instant.ofEpochMilli(in.issuedAt());
            rx.notes = in.notes();
            rxs.save(rx);
            for (ItemDto d : in.items()) {
                RxItem it = new RxItem();
                it.id = d.id() == null ? Ids.next() : d.id();
                it.clinicId = rx.clinicId;
                it.prescriptionId = rx.id;
                it.drugName = d.drugName();
                it.dose = d.dose();
                it.frequency = d.frequency();
                it.durationDays = d.durationDays();
                it.instructions = d.instructions();
                items.save(it);
            }
            rxs.flush();
            audit.record("ISSUE", "prescription", rx.id, Map.of("items", in.items().size()));
            return load(List.of(rx.id)).get(0);
        }

        @Transactional(readOnly = true)
        List<RxDto> forPatient(UUID patientId) {
            return map(rxs.findByPatientIdOrderByIssuedAtDesc(patientId));
        }

        @Transactional(readOnly = true)
        List<RxDto> load(Collection<UUID> ids) {
            return map(rxs.findAllById(ids));
        }

        private List<RxDto> map(List<Rx> list) {
            Map<UUID, List<RxItem>> byRx = items.findByPrescriptionIdIn(list.stream().map(r -> r.id).toList()).stream()
                    .collect(Collectors.groupingBy(i -> i.prescriptionId));
            return list.stream().map(r -> new RxDto(r.id, r.clinicId, r.patientId, r.visitId, r.dentistId,
                    r.issuedAt.toEpochMilli(), r.notes, byRx.getOrDefault(r.id, List.of()).stream()
                    .map(i -> new ItemDto(i.id, i.prescriptionId, i.drugName, i.dose, i.frequency, i.durationDays, i.instructions))
                    .toList())).toList();
        }
    }
}
