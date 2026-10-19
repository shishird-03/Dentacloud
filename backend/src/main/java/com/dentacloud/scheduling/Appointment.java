package com.dentacloud.scheduling;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "appointment")
class Appointment {
    static final Set<String> STATUSES = Set.of("BOOKED", "ARRIVED", "IN_CHAIR", "DONE", "NO_SHOW", "CANCELLED");

    @Id UUID id;
    UUID clinicId;
    UUID patientId;
    UUID dentistId;
    UUID chairId;
    UUID procedureId;
    Instant startAt;
    Instant endAt;
    String status = "BOOKED";
    String reason;
    String source;
    int colorLabel;
    String idempotencyKey;
    @Version int version;
    UUID createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
}
