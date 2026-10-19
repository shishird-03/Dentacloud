package com.dentacloud.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Only the SHA-256 of the token is stored. A family is one login's chain of rotations. */
@Entity
@Table(name = "refresh_token")
class RefreshToken {
    @Id UUID id;
    UUID userId;
    UUID clinicId;
    String tokenHash;
    UUID familyId;
    String deviceName;
    String deviceId;
    String fcmToken;
    Instant createdAt = Instant.now();
    Instant expiresAt;
    Instant revokedAt;
}
