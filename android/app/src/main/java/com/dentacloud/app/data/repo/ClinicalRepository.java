package com.dentacloud.app.data.repo;

import androidx.lifecycle.LiveData;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.ToothRecordEntity;
import com.dentacloud.app.data.local.entity.TreatmentPlanEntity;
import com.dentacloud.app.data.local.entity.VisitEntity;
import com.dentacloud.app.data.sync.PendingOps;
import com.dentacloud.app.util.Ids;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Visits, the dental chart and treatment plans (F5–F7). */
@Singleton
public class ClinicalRepository {
    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public ClinicalRepository(AppDatabase db, SessionStore session, ExecutorService io) {
        this.db = db;
        this.session = session;
        this.io = io;
    }

    // --- chart ---

    public LiveData<List<ToothRecordEntity>> chart(String patientId) {
        return db.clinical().chart(patientId);
    }

    /** Appends one chart record; history is never rewritten. surface null = whole tooth. */
    public void addRecord(String patientId, String visitId, int toothFdi, String surface, String condition, String note) {
        io.execute(() -> db.runInTransaction(() -> {
            ToothRecordEntity r = new ToothRecordEntity();
            r.id = Ids.uuid7();
            r.clinicId = session.clinicId();
            r.patientId = patientId;
            r.visitId = visitId;
            r.toothFdi = toothFdi;
            r.surface = surface;
            r.condition = condition;
            r.note = note;
            r.recordedBy = session.memberId();
            r.recordedAt = System.currentTimeMillis();
            db.clinical().insertRecords(List.of(r));
            PendingOps.enqueue(db, "tooth_record", r.id, r);
        }));
    }

    // --- visits ---

    public LiveData<List<VisitEntity>> visits(String patientId) {
        return db.clinical().visits(patientId);
    }

    public void loadVisit(String id, Consumer<VisitEntity> onLoaded) {
        io.execute(() -> onLoaded.accept(db.clinical().visit(id)));
    }

    public void saveVisit(VisitEntity v, Consumer<VisitEntity> onSaved) {
        io.execute(() -> {
            db.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                if (v.id.isEmpty()) {
                    v.id = Ids.uuid7();
                    v.clinicId = session.clinicId();
                    v.dentistId = session.memberId();
                    v.visitedAt = now;
                }
                v.updatedAt = now;
                v.version++;
                db.clinical().upsertVisit(v);
                PendingOps.enqueue(db, "visit", v.id, v);
            });
            onSaved.accept(v);
        });
    }

    // --- treatment plans ---

    public LiveData<List<TreatmentPlanEntity>> plans(String patientId) {
        return db.clinical().plans(patientId);
    }

    public LiveData<List<PlanItemEntity>> items(String planId) {
        return db.clinical().items(planId);
    }

    public void newPlan(String patientId, String title, Consumer<TreatmentPlanEntity> onSaved) {
        io.execute(() -> {
            TreatmentPlanEntity p = new TreatmentPlanEntity();
            p.id = Ids.uuid7();
            p.clinicId = session.clinicId();
            p.patientId = patientId;
            p.title = title;
            p.createdAt = System.currentTimeMillis();
            db.runInTransaction(() -> write(p, List.of()));
            onSaved.accept(p);
        });
    }

    public void addItem(String planId, String procedureId, Integer toothFdi, long estPaise) {
        change(planId, (plan, items) -> {
            PlanItemEntity it = new PlanItemEntity();
            it.id = Ids.uuid7();
            it.clinicId = plan.clinicId;
            it.planId = planId;
            it.procedureId = procedureId;
            it.toothFdi = toothFdi;
            it.estPaise = estPaise;
            it.sequence = items.size();
            items.add(it);
        });
    }

    public void setItemStatus(String planId, List<String> itemIds, String status) {
        change(planId, (plan, items) -> {
            for (PlanItemEntity it : items) if (itemIds.contains(it.id)) it.status = status;
            if ("DONE".equals(status) && "ACCEPTED".equals(plan.status)) plan.status = "IN_PROGRESS";
        });
    }

    public void setPlanStatus(String planId, String status) {
        change(planId, (plan, items) -> plan.status = status);
    }

    private void change(String planId, java.util.function.BiConsumer<TreatmentPlanEntity, List<PlanItemEntity>> edit) {
        io.execute(() -> db.runInTransaction(() -> {
            TreatmentPlanEntity plan = db.clinical().plan(planId);
            if (plan == null) return;
            List<PlanItemEntity> items = new java.util.ArrayList<>(db.clinical().itemsNow(planId));
            edit.accept(plan, items);
            write(plan, items);
        }));
    }

    /** Plans sync whole: the plan with all its items, server keeps billed prices. */
    private void write(TreatmentPlanEntity plan, List<PlanItemEntity> items) {
        long estimate = 0;
        for (PlanItemEntity it : items) if (!"DEFERRED".equals(it.status)) estimate += it.estPaise;
        plan.estimatePaise = estimate;
        plan.updatedAt = System.currentTimeMillis();
        db.clinical().upsertPlan(plan);
        db.clinical().upsertItems(items);
        PendingOps.enqueue(db, "treatment_plan", plan.id, plan, "items", items);
    }
}
