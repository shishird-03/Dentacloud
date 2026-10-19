package com.dentacloud.scheduling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * API and sync shape of an appointment; epoch-millis times and field names match the Android Room
 * entity. endAt may be 0 on create to use the procedure's default length.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentDto(
        UUID id,
        UUID clinicId,
        @NotNull UUID patientId,
        @NotNull UUID dentistId,
        UUID chairId,
        UUID procedureId,
        long startAt,
        long endAt,
        String status,
        @Size(max = 500) String reason,
        int colorLabel,
        long updatedAt,
        Integer version) {}
