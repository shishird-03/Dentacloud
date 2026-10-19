package com.dentacloud.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner and accountant view, export (page through with afterId) and verify the audit log (PRD 11, 14). */
@RestController
@RequestMapping("/api/v1/audit-events")
class AuditController {
    record AuditEventDto(long id, UUID actorId, String action, String resourceType, UUID resourceId,
                         Map<String, Object> details, Instant occurredAt, String hash) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;

    AuditController(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_audit.read')")
    @Transactional(readOnly = true)
    List<AuditEventDto> list(@RequestParam(defaultValue = "0") long afterId,
                             @RequestParam(defaultValue = "200") int limit) {
        return jdbc.query("select id, actor_id, action, resource_type, resource_id, after::text, occurred_at, hash"
                        + " from audit_event where id > ? order by id limit ?",
                (rs, i) -> new AuditEventDto(rs.getLong("id"), rs.getObject("actor_id", UUID.class), rs.getString("action"),
                        rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), details(rs.getString(6)),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), HexFormat.of().formatHex(rs.getBytes("hash"))),
                afterId, Math.clamp(limit, 1, 1000));
    }

    @GetMapping("/verify")
    @PreAuthorize("hasAuthority('SCOPE_audit.read')")
    AuditService.Verification verify() {
        return audit.verify();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(String after) {
        try {
            return after == null ? null : json.readValue(after, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
