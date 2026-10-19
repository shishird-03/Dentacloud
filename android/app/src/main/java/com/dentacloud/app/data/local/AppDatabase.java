package com.dentacloud.app.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.dentacloud.app.data.local.dao.AppointmentDao;
import com.dentacloud.app.data.local.dao.ClinicalDao;
import com.dentacloud.app.data.local.dao.InvoiceDao;
import com.dentacloud.app.data.local.dao.PatientDao;
import com.dentacloud.app.data.local.dao.PendingOpDao;
import com.dentacloud.app.data.local.dao.PrescriptionDao;
import com.dentacloud.app.data.local.dao.ProcedureDao;
import com.dentacloud.app.data.local.dao.SyncStateDao;
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

/** Offline source of truth. Every version bump ships with a Migration and a MigrationTest. */
@Database(version = 2, exportSchema = true, entities = {
        PatientEntity.class, AppointmentEntity.class, ProcedureEntity.class,
        InvoiceEntity.class, InvoiceLineEntity.class, PendingOpEntity.class, SyncStateEntity.class,
        VisitEntity.class, ToothRecordEntity.class, TreatmentPlanEntity.class, PlanItemEntity.class,
        PaymentEntryEntity.class, PrescriptionEntity.class, PrescriptionItemEntity.class})
public abstract class AppDatabase extends RoomDatabase {
    public static final String NAME = "dentacloud.db";

    public abstract PatientDao patients();
    public abstract AppointmentDao appointments();
    public abstract ProcedureDao procedures();
    public abstract InvoiceDao invoices();
    public abstract PendingOpDao pendingOps();
    public abstract SyncStateDao syncState();
    public abstract ClinicalDao clinical();
    public abstract PrescriptionDao prescriptions();
}
