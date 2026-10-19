package com.dentacloud.notification;

import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** Writes work for the background worker inside the caller's transaction (transactional outbox). */
@Component
class Outbox {
    static final String REMINDER_SMS = "reminder.sms";
    static final String SYNC_PUSH = "push.sync";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    Outbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Joins the business transaction: if the booking rolls back, so does its reminder. */
    @Transactional(propagation = Propagation.MANDATORY)
    void enqueue(String topic, Map<String, ?> payload, Instant runAfter) {
        try {
            jdbc.update("insert into outbox_event (id, clinic_id, topic, payload, run_after) values (?, ?, ?, cast(? as jsonb), ?)",
                    Ids.next(), TenantContext.require(), topic, json.writeValueAsString(payload), Timestamp.from(runAfter));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
