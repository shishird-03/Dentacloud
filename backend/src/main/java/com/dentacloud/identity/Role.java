package com.dentacloud.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Named set of scopes (PRD 3). System roles have no clinic; custom roles need no code change. */
@Entity
@Table(name = "role")
class Role {
    static final UUID OWNER = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Id UUID id;
    UUID clinicId;
    String name;
    boolean isSystem;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "scope")
    Set<String> scopes = new TreeSet<>();
}
