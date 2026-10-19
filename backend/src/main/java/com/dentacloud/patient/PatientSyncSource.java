package com.dentacloud.patient;

import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.SyncSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Patients sync last-writer-wins (PRD 9): a pushed edit overwrites, the server keeps the audit trail. */
@Component
class PatientSyncSource implements SyncSource {
    private final PatientService patients;
    private final PatientRepository repo;
    private final ObjectMapper json;
    private final Validator validator;

    PatientSyncSource(PatientService patients, PatientRepository repo, ObjectMapper json, Validator validator) {
        this.patients = patients;
        this.repo = repo;
        this.json = json;
        this.validator = validator;
    }

    @Override
    public String entity() {
        return "patient";
    }

    @Override
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        patients.load(ids).forEach(p -> out.put(p.id(), p));
        return out;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_patient.write')")
    public void upsert(JsonNode payload) {
        PatientDto in;
        try {
            in = json.treeToValue(payload, PatientDto.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("bad_payload", "Patient payload is not valid JSON for a patient");
        }
        var violations = validator.validate(in);
        if (in.id() == null || !violations.isEmpty()) {
            throw ApiException.badRequest("bad_payload", in.id() == null ? "id is required"
                    : violations.iterator().next().getPropertyPath() + " " + violations.iterator().next().getMessage());
        }
        if (repo.existsById(in.id())) patients.update(in.id(), in, null);
        else patients.create(in);
    }
}
