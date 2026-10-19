package com.dentacloud.billing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line of the clinic's price list (F8). Money is integer paise; tax is basis points (1800 = 18%). */
@Entity
@Table(name = "procedure_catalog")
class Procedure {
    @Id UUID id;
    UUID clinicId;
    String code;
    String name;
    long defaultPaise;
    int defaultMinutes = 30;
    int taxBasisPoints;
    Instant updatedAt = Instant.now();
}
