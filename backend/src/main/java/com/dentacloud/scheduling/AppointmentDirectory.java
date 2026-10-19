package com.dentacloud.scheduling;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about appointments (the reminder worker re-checks before sending). */
@Component
public class AppointmentDirectory {
    public record ReminderInfo(UUID id, UUID patientId, Instant startAt, String status) {}

    private final AppointmentRepository repo;

    AppointmentDirectory(AppointmentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<ReminderInfo> find(UUID id) {
        return repo.findById(id).map(a -> new ReminderInfo(a.id, a.patientId, a.startAt, a.status));
    }
}
