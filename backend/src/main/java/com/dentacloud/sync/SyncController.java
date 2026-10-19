package com.dentacloud.sync;

import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.SyncSource;
import com.dentacloud.shared.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mobile sync (PRD 9). Pull: changes after a server sequence number. Push: a batch of device writes,
 * each with an idempotency key so a retried batch is applied once.
 */
@RestController
@RequestMapping("/api/v1/sync")
class SyncController {
    record Change(long seq, String entity, UUID id, Object data) {}

    record PullResponse(List<Change> changes, long nextSince, boolean hasMore) {}

    record PushOp(@NotBlank String idempotencyKey, @NotBlank String entity, @NotNull UUID entityId,
                  @NotBlank String operation, @NotNull JsonNode payload) {}

    record PushRequest(@NotNull @Size(max = 200) List<@Valid PushOp> ops) {}

    record PushResult(String idempotencyKey, String status, String code, String message) {
        static PushResult applied(String key) { return new PushResult(key, "APPLIED", null, null); }
    }

    record PushResponse(List<PushResult> results) {}

    private record Row(long seq, String entity, UUID id) {}

    private final Map<String, SyncSource> sources;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Duration lag;

    SyncController(List<SyncSource> sources, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                   ObjectMapper json, @Value("${dentacloud.sync-lag:5s}") Duration lag) {
        this.sources = sources.stream().collect(Collectors.toMap(SyncSource::entity, Function.identity()));
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.json = json;
        this.lag = lag;
    }

    /**
     * Returns the latest state of every row changed after {@code since}.
     * ponytail: rows younger than the lag are held back so a transaction that took a lower seq but
     * committed later is not skipped; switch to xid8 snapshot filtering if gaps ever show up.
     */
    @GetMapping("/pull")
    @PreAuthorize("hasAuthority('SCOPE_patient.read')")
    PullResponse pull(@RequestParam(defaultValue = "0") long since, @RequestParam(defaultValue = "500") int limit) {
        int max = Math.min(Math.max(limit, 1), 1000);
        return tx.execute(s -> {
            List<Row> rows = jdbc.query("select seq, entity, entity_id from sync_change"
                            + " where seq > ? and changed_at < now() - make_interval(secs => ?) order by seq limit ?",
                    (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class)),
                    since, (double) lag.toMillis() / 1000, max);

            Map<String, Row> latest = new LinkedHashMap<>(); // one entry per row, newest seq wins
            for (Row r : rows) {
                latest.remove(r.entity() + ":" + r.id());
                latest.put(r.entity() + ":" + r.id(), r);
            }
            Map<String, Map<UUID, Object>> loaded = new HashMap<>();
            latest.values().stream().collect(Collectors.groupingBy(Row::entity, Collectors.mapping(Row::id, Collectors.toList())))
                    .forEach((entity, ids) -> {
                        SyncSource src = sources.get(entity);
                        if (src != null) loaded.put(entity, src.load(ids));
                    });
            List<Change> changes = new ArrayList<>();
            for (Row r : latest.values()) {
                Object data = loaded.getOrDefault(r.entity(), Map.of()).get(r.id());
                if (data != null) changes.add(new Change(r.seq(), r.entity(), r.id(), data));
            }
            long next = rows.isEmpty() ? since : rows.get(rows.size() - 1).seq();
            return new PullResponse(changes, next, rows.size() == max);
        });
    }

    @PostMapping("/push")
    PushResponse push(@Valid @RequestBody PushRequest req) {
        UUID clinic = TenantContext.require();
        List<PushResult> results = new ArrayList<>();
        for (PushOp op : req.ops()) {
            PushResult previous = tx.execute(s -> jdbc.query(
                    "select result from sync_push_log where idempotency_key = ?",
                    rs -> rs.next() ? read(rs.getString(1)) : null, op.idempotencyKey()));
            if (previous != null) {
                results.add(previous);
                continue;
            }
            PushResult r = apply(op);
            tx.executeWithoutResult(s -> jdbc.update("insert into sync_push_log (clinic_id, idempotency_key, result)"
                            + " values (?, ?, cast(? as jsonb)) on conflict do nothing",
                    clinic, op.idempotencyKey(), write(r)));
            results.add(r);
        }
        return new PushResponse(results);
    }

    /** Each op in its own transaction: one conflict must not undo the rest of the batch. */
    private PushResult apply(PushOp op) {
        SyncSource src = sources.get(op.entity());
        if (src == null) return new PushResult(op.idempotencyKey(), "REJECTED", "unknown_entity", "Unknown entity " + op.entity());
        if (!"UPSERT".equals(op.operation())) {
            return new PushResult(op.idempotencyKey(), "REJECTED", "unknown_operation", "Only UPSERT is supported");
        }
        try {
            tx.executeWithoutResult(s -> src.upsert(op.payload()));
            return PushResult.applied(op.idempotencyKey());
        } catch (ApiException e) {
            String status = e.status() == HttpStatus.CONFLICT ? "CONFLICT" : "REJECTED";
            return new PushResult(op.idempotencyKey(), status, e.code(), e.getMessage());
        } catch (AccessDeniedException e) {
            return new PushResult(op.idempotencyKey(), "REJECTED", "forbidden", "Your role cannot change " + op.entity());
        }
    }

    private PushResult read(String s) {
        try {
            return json.readValue(s, PushResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(PushResult r) {
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
