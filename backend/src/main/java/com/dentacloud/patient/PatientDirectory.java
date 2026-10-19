package com.dentacloud.patient;

import com.dentacloud.shared.FieldCrypto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about patients. Foreign keys skip RLS, so callers check here first. */
@Component
public class PatientDirectory {
    /** Only what a reminder needs: never the name or any clinical field. */
    public record ReminderContact(String phone, boolean optedOut) {}

    private final PatientRepository repo;
    private final FieldCrypto crypto;

    PatientDirectory(PatientRepository repo, FieldCrypto crypto) {
        this.repo = repo;
        this.crypto = crypto;
    }

    /** True only for a non-deleted patient of the current clinic. */
    @Transactional(readOnly = true)
    public boolean exists(UUID patientId) {
        return patientId != null && repo.findById(patientId).filter(p -> p.deletedAt == null).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<ReminderContact> reminderContact(UUID patientId) {
        return repo.findById(patientId).filter(p -> p.deletedAt == null)
                .map(p -> new ReminderContact(crypto.decrypt(p.phoneEnc), p.remindersOptOut));
    }
}
