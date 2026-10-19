package com.dentacloud.app.data.sync;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.TokenStore;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.InvoiceLineEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;
import com.dentacloud.app.data.local.entity.PendingOpEntity;
import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.PrescriptionEntity;
import com.dentacloud.app.data.local.entity.PrescriptionItemEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.local.entity.SyncStateEntity;
import com.dentacloud.app.data.local.entity.ToothRecordEntity;
import com.dentacloud.app.data.local.entity.TreatmentPlanEntity;
import com.dentacloud.app.data.local.entity.VisitEntity;
import com.dentacloud.app.data.remote.api.SyncControllerApi;
import com.dentacloud.app.data.remote.dto.Change;
import com.dentacloud.app.data.remote.dto.PullResponse;
import com.dentacloud.app.data.remote.dto.PushOp;
import com.dentacloud.app.data.remote.dto.PushRequest;
import com.dentacloud.app.data.remote.dto.PushResponse;
import com.dentacloud.app.data.remote.dto.PushResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

/**
 * Push the pending_op queue, then pull server changes after the last seq (PRD 9). Runs on a
 * WorkManager thread. Rows with unpushed local edits are not overwritten by the pull.
 */
@Singleton
public class SyncEngine {
    public enum Outcome { SYNCED, SKIPPED }

    static final int BATCH = 50;
    private static final int PULL_PAGE = 500;

    private final AppDatabase db;
    private final SyncControllerApi api;
    private final SessionStore session;
    private final TokenStore tokens;
    private final Gson gson;

    @Inject
    public SyncEngine(AppDatabase db, SyncControllerApi api, SessionStore session, TokenStore tokens, Gson gson) {
        this.db = db;
        this.api = api;
        this.session = session;
        this.tokens = tokens;
        this.gson = gson;
    }

    /** Throws IOException when offline or the server failed; WorkManager retries with backoff. */
    public synchronized Outcome run() throws IOException {
        if (session.isDemo() || !session.isLoggedIn() || !tokens.hasSession()) return Outcome.SKIPPED;
        push();
        pull();
        return Outcome.SYNCED;
    }

    private void push() throws IOException {
        List<PendingOpEntity> all = db.pendingOps().pushable(1000);
        for (int from = 0; from < all.size(); from += BATCH) {
            List<PendingOpEntity> batch = all.subList(from, Math.min(all.size(), from + BATCH));
            PushRequest req = new PushRequest();
            Map<String, PendingOpEntity> byKey = new HashMap<>();
            for (PendingOpEntity op : batch) {
                byKey.put(op.idempotencyKey, op);
                // A JsonElement keeps the original number text (no 1.7E12-style doubles).
                req.addOpsItem(new PushOp().idempotencyKey(op.idempotencyKey).entity(op.entity)
                        .entityId(UUID.fromString(op.entityId)).operation(op.operation)
                        .payload(JsonParser.parseString(op.payloadJson)));
            }
            Response<PushResponse> r = api.syncPush(req).execute();
            if (!r.isSuccessful() || r.body() == null) throw new IOException("push failed: HTTP " + r.code());
            db.runInTransaction(() -> {
                for (PushResult res : r.body().getResults()) {
                    PendingOpEntity op = byKey.get(res.getIdempotencyKey());
                    if (op != null) settle(op, res);
                }
            });
        }
    }

    private void settle(PendingOpEntity op, PushResult res) {
        switch (res.getStatus()) {
            case "APPLIED" -> db.pendingOps().delete(op.id);
            case "CONFLICT" -> {
                // Server wins. A booking that lost its slot stays visible as cancelled so staff can rebook.
                if ("appointment".equals(op.entity) && "slot_taken".equals(res.getCode())) {
                    AppointmentEntity a = db.appointments().get(op.entityId);
                    if (a != null) {
                        a.status = AppointmentEntity.CANCELLED;
                        a.reason = "Slot taken on another device. Please rebook.";
                        db.appointments().upsert(a);
                    }
                }
                db.pendingOps().delete(op.id);
            }
            default -> {
                // The server doesn't know this entity yet (newer app than server): keep it for later.
                if ("unknown_entity".equals(res.getCode())) db.pendingOps().markAttempted(op.id);
                else db.pendingOps().markFailed(op.id, res.getMessage());
            }
        }
    }

    private void pull() throws IOException {
        String clinicId = session.clinicId();
        SyncStateEntity state = db.syncState().get(clinicId);
        if (state == null) {
            state = new SyncStateEntity();
            state.clinicId = clinicId;
        }
        boolean more = true;
        while (more) {
            Response<PullResponse> r = api.syncPull(state.lastSeq, PULL_PAGE).execute();
            if (!r.isSuccessful() || r.body() == null) throw new IOException("pull failed: HTTP " + r.code());
            PullResponse page = r.body();
            Set<String> dirty = new HashSet<>(db.pendingOps().pendingEntityIds());
            SyncStateEntity st = state;
            db.runInTransaction(() -> {
                for (Change c : page.getChanges()) {
                    if (!dirty.contains(c.getId().toString())) store(c.getEntity(), gson.toJsonTree(c.getData()));
                }
                st.lastSeq = page.getNextSince();
                st.lastSyncedAt = System.currentTimeMillis();
                db.syncState().upsert(st);
            });
            more = Boolean.TRUE.equals(page.getHasMore());
        }
    }

    private <T> List<T> children(JsonElement parent, String key, Class<T[]> type) {
        JsonElement arr = parent.getAsJsonObject().get(key);
        return arr == null || arr.isJsonNull() ? List.of() : List.of(gson.fromJson(arr, type));
    }

    /** Writes a server row straight to Room: no pending_op, it is already on the server. */
    private void store(String entity, JsonElement data) {
        switch (entity) {
            case "patient" -> {
                PatientEntity p = gson.fromJson(data, PatientEntity.class);
                PatientEntity local = db.patients().get(p.id);
                if (local != null) p.photoPath = local.photoPath; // photos stay on the device until M4 uploads
                db.patients().upsert(p);
            }
            case "appointment" -> db.appointments().upsert(gson.fromJson(data, AppointmentEntity.class));
            case "procedure" -> db.procedures().upsertAll(List.of(gson.fromJson(data, ProcedureEntity.class)));
            case "visit" -> db.clinical().upsertVisit(gson.fromJson(data, VisitEntity.class));
            case "tooth_record" -> db.clinical().insertRecords(List.of(gson.fromJson(data, ToothRecordEntity.class)));
            case "treatment_plan" -> {
                db.clinical().upsertPlan(gson.fromJson(data, TreatmentPlanEntity.class));
                db.clinical().upsertItems(children(data, "items", PlanItemEntity[].class));
            }
            case "invoice" -> {
                db.invoices().upsert(gson.fromJson(data, InvoiceEntity.class));
                db.invoices().upsertLines(children(data, "lines", InvoiceLineEntity[].class));
            }
            case "payment" -> db.invoices().insertPayment(gson.fromJson(data, PaymentEntryEntity.class));
            case "prescription" -> {
                db.prescriptions().upsert(gson.fromJson(data, PrescriptionEntity.class));
                db.prescriptions().upsertItems(children(data, "items", PrescriptionItemEntity[].class));
            }
            default -> { } // entities this app version doesn't store yet
        }
    }
}
