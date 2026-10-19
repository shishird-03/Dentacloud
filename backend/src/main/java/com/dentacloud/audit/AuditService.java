package com.dentacloud.audit;

import com.dentacloud.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only, hash-chained audit log (PRD 6, 14). Callers pass actions and field names, never
 * patient values: the log must not become an unencrypted copy of the chart.
 */
@Service
public class AuditService {
    /** Result of re-walking a clinic's chain; {@code firstBrokenId} is null when it is intact. */
    public record Verification(boolean valid, long checked, Long firstBrokenId) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Joins the caller's transaction so the audit row commits or rolls back with the change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String resourceType, UUID resourceId, Map<String, ?> details) {
        UUID clinic = TenantContext.require();
        UUID actor = actor();
        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS); // Postgres keeps microseconds
        try {
            // Hash Postgres's own rendering of the jsonb, so verify() can recompute it from the stored row.
            String after = details == null ? null
                    : jdbc.queryForObject("select cast(? as jsonb)::text", String.class, json.writeValueAsString(details));
            // Serialise writers per clinic so two events never chain onto the same predecessor.
            jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, clinic.toString());
            byte[] prev = jdbc.query("select hash from audit_event where clinic_id = ? order by id desc limit 1",
                    rs -> rs.next() ? rs.getBytes(1) : null, clinic);
            jdbc.update("insert into audit_event (clinic_id, actor_id, action, resource_type, resource_id, after, occurred_at, prev_hash, hash)"
                            + " values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)",
                    clinic, actor, action, resourceType, resourceId, after, Timestamp.from(at), prev,
                    hash(prev, clinic, actor, action, resourceType, resourceId, after, at));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clinical reads have no write transaction to join, so each commits on its own. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRead(String resourceType, UUID resourceId, Map<String, ?> details) {
        record("READ", resourceType, resourceId, details);
    }

    /**
     * Re-walks the current clinic's chain: every row must link to the previous row's hash and hash to
     * what it stores. Catches edits and deletions in the middle; dropping the newest rows needs the
     * head hash compared against a copy kept elsewhere.
     * ponytail: full scan per call; checkpoint verified ids if chains get long.
     */
    @Transactional(readOnly = true)
    public Verification verify() {
        UUID clinic = TenantContext.require();
        long[] checked = {0};
        byte[][] prev = {null};
        Long[] broken = {null};
        jdbc.query("select id, actor_id, action, resource_type, resource_id, after::text, occurred_at, prev_hash, hash"
                + " from audit_event order by id", rs -> {
            if (broken[0] != null) return;
            checked[0]++;
            byte[] stored = rs.getBytes("hash");
            byte[] expected = hash(prev[0], clinic, rs.getObject("actor_id", UUID.class), rs.getString("action"),
                    rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), rs.getString(6),
                    rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
            if (!Arrays.equals(rs.getBytes("prev_hash"), prev[0]) || !Arrays.equals(stored, expected)) {
                broken[0] = rs.getLong("id");
            }
            prev[0] = stored;
        });
        return new Verification(broken[0] == null, checked[0], broken[0]);
    }

    private static byte[] hash(byte[] prev, UUID clinic, UUID actor, String action, String resourceType,
                               UUID resourceId, String after, Instant at) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            if (prev != null) sha.update(prev);
            sha.update(String.join("|", clinic.toString(), String.valueOf(actor), action, resourceType,
                    String.valueOf(resourceId), String.valueOf(after), at.toString()).getBytes(StandardCharsets.UTF_8));
            return sha.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        try {
            return a == null ? null : UUID.fromString(a.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
