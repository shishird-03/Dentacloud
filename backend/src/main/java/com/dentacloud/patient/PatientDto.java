package com.dentacloud.patient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * API and sync shape of a patient. Field names and epoch-millis times match the Android Room
 * entity so pulled rows can be stored as-is. patientNo, clinicId and timestamps are server-owned.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDto(
        UUID id,
        UUID clinicId,
        String patientNo,
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dob,
        String gender,
        @Pattern(regexp = "\\+?[0-9 ]{7,16}") String phone,
        @Size(max = 200) String email,
        @Size(max = 500) String address,
        @Size(max = 4000) String medicalHistory,
        @Size(max = 1000) String allergies,
        int cardColor,
        Boolean remindersOptOut,
        UUID familyHeadId,
        long createdAt,
        long updatedAt,
        Integer version,
        Long deletedAt) {}
