package com.dentacloud.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A person who can log in. Global (not tenant-scoped): one login can belong to several clinics. */
@Entity
@Table(name = "app_user")
class AppUser {
    @Id UUID id;
    String email;
    String phone;
    String passwordHash;
    String fullName;
    String status = "ACTIVE";
    Instant lastLoginAt;
    Instant createdAt = Instant.now();

    /** Stored and looked up lowercase: JDBC binds varchar, which would skip citext's case folding. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
