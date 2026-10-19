package com.dentacloud.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A user's seat in one clinic; appointments and visits reference this id. */
@Entity
@Table(name = "clinic_member")
class ClinicMember {
    @Id UUID id;
    UUID clinicId;
    UUID userId;
    UUID roleId;
    boolean isDentist;
    String registrationNo;
    String color;
    String status = "ACTIVE";
}
