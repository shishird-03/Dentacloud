package com.dentacloud.patient;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contact and medical fields are stored only as ciphertext (*Enc); PatientService maps them. */
@Entity
@Table(name = "patient")
class Patient {
    @Id UUID id;
    UUID clinicId;
    String patientNo;
    String fullName;
    LocalDate dob;
    String gender;
    byte[] phoneEnc;
    byte[] phoneBlindIdx;
    byte[] emailEnc;
    byte[] addressEnc;
    byte[] medicalHistoryEnc;
    byte[] allergiesEnc;
    UUID familyHeadId;
    boolean marketingOptIn;
    int cardColor;
    boolean remindersOptOut;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    UUID createdBy;
    Instant deletedAt;
    @Version int version;
}
