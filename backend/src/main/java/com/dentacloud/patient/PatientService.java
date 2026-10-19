package com.dentacloud.patient;

import com.dentacloud.audit.AuditService;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.FieldCrypto;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
class PatientService {
    private final PatientRepository repo;
    private final FieldCrypto crypto;
    private final AuditService audit;
    private final EntityManager em;

    PatientService(PatientRepository repo, FieldCrypto crypto, AuditService audit, EntityManager em) {
        this.repo = repo;
        this.crypto = crypto;
        this.audit = audit;
        this.em = em;
    }

    /** Name contains, exact phone (via blind index) or exact patient number. Under 200 ms via indexes. */
    @Transactional(readOnly = true)
    List<PatientDto> search(String q, int limit, UUID cursor) {
        Limit max = Limit.of(Math.min(Math.max(limit, 1), 200));
        if (q == null || q.isBlank()) {
            return map(cursor == null ? repo.findByDeletedAtIsNullOrderByIdDesc(max)
                    : repo.findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(cursor, max));
        }
        String term = q.trim();
        if (term.replaceAll("\\D", "").length() >= 7 && term.matches("[+0-9 ()-]+")) {
            return map(repo.findByPhoneBlindIdxAndDeletedAtIsNull(crypto.phoneIndex(term)));
        }
        if (term.toUpperCase(Locale.ROOT).matches("P-\\d+")) {
            return map(repo.findByPatientNoAndDeletedAtIsNull(term.toUpperCase(Locale.ROOT)).stream().toList());
        }
        return map(repo.findByFullNameContainingIgnoreCaseAndDeletedAtIsNullOrderByFullName(term, max));
    }

    @Transactional(readOnly = true)
    PatientDto get(UUID id) {
        return dto(find(id));
    }

    @Transactional(readOnly = true)
    List<PatientDto> load(Collection<UUID> ids) {
        return map(repo.findAllById(ids));
    }

    /** Creates a patient; a repeated create with the same client-generated id returns the original. */
    @Transactional
    PatientDto create(PatientDto in) {
        if (in.id() != null) {
            var existing = repo.findById(in.id());
            if (existing.isPresent()) return dto(existing.get());
        }
        Patient p = new Patient();
        p.id = in.id() == null ? Ids.next() : in.id();
        p.clinicId = TenantContext.require();
        p.patientNo = nextPatientNo();
        apply(p, in);
        repo.save(p);
        audit.record("CREATE", "patient", p.id, null);
        return dto(p);
    }

    /** Updates; a non-null expectedVersion that is stale fails with 409 (sync uses last-writer-wins). */
    @Transactional
    PatientDto update(UUID id, PatientDto in, Integer expectedVersion) {
        Patient p = find(id);
        if (expectedVersion != null && expectedVersion != p.version) {
            throw ApiException.conflict("stale_version", "This patient was changed by someone else; reload and retry");
        }
        List<String> changed = apply(p, in);
        p.updatedAt = Instant.now();
        if (!changed.isEmpty()) audit.record("UPDATE", "patient", p.id, java.util.Map.of("fields", changed));
        repo.saveAndFlush(p); // flush so the returned version is the incremented one
        return dto(p);
    }

    /** Copies client-editable fields; returns the names of fields that changed (for the audit log). */
    private List<String> apply(Patient p, PatientDto in) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(p.fullName, in.fullName())) { p.fullName = in.fullName().trim(); changed.add("fullName"); }
        LocalDate dob = in.dob() == null ? null : LocalDate.parse(in.dob());
        if (!Objects.equals(p.dob, dob)) { p.dob = dob; changed.add("dob"); }
        if (!Objects.equals(p.gender, in.gender())) { p.gender = in.gender(); changed.add("gender"); }
        if (!Objects.equals(crypto.decrypt(p.phoneEnc), in.phone())) {
            p.phoneEnc = crypto.encrypt(in.phone());
            p.phoneBlindIdx = crypto.phoneIndex(in.phone());
            changed.add("phone");
        }
        if (!Objects.equals(crypto.decrypt(p.emailEnc), in.email())) { p.emailEnc = crypto.encrypt(in.email()); changed.add("email"); }
        if (!Objects.equals(crypto.decrypt(p.addressEnc), in.address())) { p.addressEnc = crypto.encrypt(in.address()); changed.add("address"); }
        if (!Objects.equals(crypto.decrypt(p.medicalHistoryEnc), in.medicalHistory())) {
            p.medicalHistoryEnc = crypto.encrypt(in.medicalHistory());
            changed.add("medicalHistory");
        }
        if (!Objects.equals(crypto.decrypt(p.allergiesEnc), in.allergies())) { p.allergiesEnc = crypto.encrypt(in.allergies()); changed.add("allergies"); }
        if (p.cardColor != in.cardColor()) { p.cardColor = in.cardColor(); changed.add("cardColor"); }
        if (in.remindersOptOut() != null && p.remindersOptOut != in.remindersOptOut()) {
            p.remindersOptOut = in.remindersOptOut();
            changed.add("remindersOptOut");
        }
        if (!Objects.equals(p.familyHeadId, in.familyHeadId())) { p.familyHeadId = in.familyHeadId(); changed.add("familyHeadId"); }
        boolean deleted = in.deletedAt() != null;
        if (deleted != (p.deletedAt != null)) {
            p.deletedAt = deleted ? Instant.ofEpochMilli(in.deletedAt()) : null;
            changed.add("deletedAt");
        }
        return changed;
    }

    private String nextPatientNo() {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtext('patient_no:' || app_clinic()::text))").getSingleResult();
        Number max = (Number) em.createNativeQuery(
                "select coalesce(max(cast(substring(patient_no from 3) as int)), 0) from patient where patient_no ~ '^P-[0-9]+$'")
                .getSingleResult();
        return String.format(Locale.ROOT, "P-%04d", max.intValue() + 1);
    }

    private Patient find(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Patient"));
    }

    private List<PatientDto> map(List<Patient> list) {
        return list.stream().map(this::dto).toList();
    }

    PatientDto dto(Patient p) {
        return new PatientDto(p.id, p.clinicId, p.patientNo, p.fullName, p.dob == null ? null : p.dob.toString(),
                p.gender, crypto.decrypt(p.phoneEnc), crypto.decrypt(p.emailEnc), crypto.decrypt(p.addressEnc),
                crypto.decrypt(p.medicalHistoryEnc), crypto.decrypt(p.allergiesEnc), p.cardColor, p.remindersOptOut, p.familyHeadId,
                p.createdAt.toEpochMilli(), p.updatedAt.toEpochMilli(), p.version,
                p.deletedAt == null ? null : p.deletedAt.toEpochMilli());
    }
}
