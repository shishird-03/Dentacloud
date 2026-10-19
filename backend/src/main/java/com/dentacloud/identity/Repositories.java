package com.dentacloud.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ClinicRepository extends JpaRepository<Clinic, UUID> {}

interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email); // email is citext, so this is case-insensitive
    boolean existsByEmail(String email);
}

interface RoleRepository extends JpaRepository<Role, UUID> {}

interface ClinicMemberRepository extends JpaRepository<ClinicMember, UUID> {
    List<ClinicMember> findAllByOrderByIdAsc();
}

interface ChairRepository extends JpaRepository<Chair, UUID> {
    List<Chair> findAllByOrderByNameAsc();
}

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :family and t.revokedAt is null")
    int revokeFamily(UUID family, Instant now);
}
