package com.dentacloud.app.data.sync;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.entity.PendingOpEntity;
import com.dentacloud.app.util.Ids;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

/** Every local write also queues a pending_op in the same transaction; SyncWorker pushes them. */
public final class PendingOps {
    public static final String UPSERT = "UPSERT";
    private static final Gson GSON = new Gson();

    private PendingOps() {}

    public static void enqueue(AppDatabase db, String entity, String entityId, Object payload) {
        insert(db, entity, entityId, GSON.toJson(payload));
    }

    /** Parent fields plus its children under {@code childKey}, the shape the server's sync expects. */
    public static void enqueue(AppDatabase db, String entity, String entityId, Object parent, String childKey, List<?> children) {
        JsonObject json = GSON.toJsonTree(parent).getAsJsonObject();
        json.add(childKey, GSON.toJsonTree(children));
        insert(db, entity, entityId, json.toString());
    }

    private static void insert(AppDatabase db, String entity, String entityId, String payload) {
        PendingOpEntity op = new PendingOpEntity();
        op.id = Ids.uuid7();
        op.entity = entity;
        op.entityId = entityId;
        op.operation = UPSERT;
        op.payloadJson = payload;
        op.idempotencyKey = op.id;
        op.createdAt = System.currentTimeMillis();
        db.pendingOps().insert(op);
    }
}
