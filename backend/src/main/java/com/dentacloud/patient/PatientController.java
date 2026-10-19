package com.dentacloud.patient;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
class PatientController {
    record Page(List<PatientDto> items, UUID nextCursor) {}

    private final PatientService patients;

    PatientController(PatientService patients) {
        this.patients = patients;
    }

    /** {@code q} matches name (contains), phone (exact, any format) or patient number. */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_patient.read')")
    Page list(@RequestParam(required = false) String q,
              @RequestParam(defaultValue = "50") int limit,
              @RequestParam(required = false) UUID cursor) {
        List<PatientDto> items = patients.search(q, limit, cursor);
        boolean paged = (q == null || q.isBlank()) && items.size() == Math.min(Math.max(limit, 1), 200);
        return new Page(items, paged ? items.get(items.size() - 1).id() : null);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_patient.write')")
    @ResponseStatus(HttpStatus.CREATED)
    PatientDto create(@Valid @RequestBody PatientDto body) {
        return patients.create(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_patient.read')")
    PatientDto get(@PathVariable UUID id) {
        return patients.get(id);
    }

    /** Send the version you read; a stale one returns 409. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_patient.write')")
    PatientDto update(@PathVariable UUID id, @Valid @RequestBody PatientDto body) {
        return patients.update(id, body, body.version());
    }
}
