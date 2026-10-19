package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local-only outbox of writes waiting to be pushed to the server (PRD 9, 10). */
@Entity(tableName = "pending_op")
public class PendingOpEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String entity = "";
    @NonNull public String entityId = "";
    @NonNull public String operation = "";     // UPSERT, DELETE
    @NonNull public String payloadJson = "";
    @NonNull public String idempotencyKey = "";
    public long createdAt;
    public int attempts;
    public String lastError;
}
