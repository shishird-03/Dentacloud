package com.dentacloud.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(Instant from, Instant to);

    List<Appointment> findByDentistIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(UUID dentistId, Instant from, Instant to);

    Optional<Appointment> findByIdempotencyKey(String key);
}
