package com.dentacloud.scheduling;

import java.time.Instant;
import java.util.UUID;

/** Published inside the booking or reschedule transaction (tenant bound); reminders hang off it. */
public record AppointmentScheduled(UUID appointmentId, UUID clinicId, Instant startAt) {}
