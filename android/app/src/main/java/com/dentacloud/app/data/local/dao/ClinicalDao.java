package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.ToothRecordEntity;
import com.dentacloud.app.data.local.entity.TreatmentPlanEntity;
import com.dentacloud.app.data.local.entity.VisitEntity;

import java.util.List;

@Dao
public interface ClinicalDao {
    @Upsert
    void upsertVisit(VisitEntity v);

    @Query("SELECT * FROM visit WHERE patientId = :patientId ORDER BY visitedAt DESC")
    LiveData<List<VisitEntity>> visits(String patientId);

    @Query("SELECT * FROM visit WHERE id = :id")
    VisitEntity visit(String id);

    /** Chart history is append-only: an existing id is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertRecords(List<ToothRecordEntity> records);

    @Query("SELECT * FROM tooth_record WHERE patientId = :patientId ORDER BY recordedAt, id")
    LiveData<List<ToothRecordEntity>> chart(String patientId);

    @Upsert
    void upsertPlan(TreatmentPlanEntity p);

    @Upsert
    void upsertItems(List<PlanItemEntity> items);

    @Query("SELECT * FROM treatment_plan WHERE patientId = :patientId ORDER BY createdAt DESC")
    LiveData<List<TreatmentPlanEntity>> plans(String patientId);

    @Query("SELECT * FROM treatment_plan WHERE id = :id")
    TreatmentPlanEntity plan(String id);

    @Query("SELECT * FROM plan_item WHERE planId = :planId ORDER BY sequence")
    LiveData<List<PlanItemEntity>> items(String planId);

    @Query("SELECT * FROM plan_item WHERE planId = :planId ORDER BY sequence")
    List<PlanItemEntity> itemsNow(String planId);

    /** Plan items already on an invoice (each item is billed once). */
    @Query("SELECT planItemId FROM invoice_line WHERE planItemId IN (:itemIds)")
    List<String> billedItemIds(List<String> itemIds);
}
