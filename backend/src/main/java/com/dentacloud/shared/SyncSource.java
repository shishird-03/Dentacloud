package com.dentacloud.shared;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * A module's synced table, as seen by the sync module. Payloads use the Android Room field names
 * so the app can store pulled rows directly (PRD 9).
 */
public interface SyncSource {
    /** Entity name used in sync_change and pending_op ("patient", "appointment", ...). */
    String entity();

    /** Current state of the given rows, keyed by id. Missing ids were not visible to this tenant. */
    Map<UUID, Object> load(Collection<UUID> ids);

    /** Applies one pushed UPSERT. Throws ApiException (409 = conflict, 4xx = rejected). */
    void upsert(JsonNode payload);
}
