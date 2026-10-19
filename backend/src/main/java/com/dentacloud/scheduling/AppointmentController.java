package com.dentacloud.scheduling;

import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.SyncSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController implements SyncSource {
    record StatusChange(@NotBlank String status) {}

    record Reschedule(long startAt, long endAt, Integer version) {}

    private final AppointmentService appointments;
    private final ObjectMapper json;

    AppointmentController(AppointmentService appointments, ObjectMapper json) {
        this.appointments = appointments;
        this.json = json;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_appointment.read')")
    List<AppointmentDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                              @RequestParam(required = false) UUID dentistId) {
        return appointments.list(from, to, dentistId);
    }

    /** Idempotency-Key is required (PRD 11): retrying a booking after a timeout must not double-book. */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_appointment.write')")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentDto create(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody AppointmentDto body) {
        return appointments.create(body, idempotencyKey, "app");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SCOPE_appointment.status')")
    AppointmentDto status(@PathVariable UUID id, @Valid @RequestBody StatusChange body) {
        return appointments.setStatus(id, body.status());
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('SCOPE_appointment.write')")
    AppointmentDto reschedule(@PathVariable UUID id, @RequestBody Reschedule body) {
        return appointments.reschedule(id, body.startAt(), body.endAt(), body.version());
    }

    // --- sync ---

    @Override
    public String entity() {
        return "appointment";
    }

    @Override
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        appointments.load(ids).forEach(a -> out.put(a.id(), a));
        return out;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('SCOPE_appointment.write', 'SCOPE_appointment.status')")
    public void upsert(JsonNode payload) {
        AppointmentDto in;
        try {
            in = json.treeToValue(payload, AppointmentDto.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("bad_payload", "Not a valid appointment");
        }
        if (in.id() == null || in.patientId() == null || in.dentistId() == null) {
            throw ApiException.badRequest("bad_payload", "id, patientId and dentistId are required");
        }
        appointments.applyFromDevice(in);
    }
}
