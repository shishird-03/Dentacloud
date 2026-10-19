package com.dentacloud.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "chair")
class Chair {
    @Id UUID id;
    UUID clinicId;
    String name;
    boolean active = true;
}
