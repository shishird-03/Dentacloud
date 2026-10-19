package com.dentacloud.app.di;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Room migrations in version order; MigrationTest runs each one against the exported schemas. */
public final class Migrations {
    /** M4: visits, chart history, treatment plans, payment ledger, prescriptions. SQL copied from schemas/2.json. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `visit` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `appointmentId` TEXT, `dentistId` TEXT, `visitedAt` INTEGER NOT NULL, `complaint` TEXT, `diagnosis` TEXT, `notes` TEXT, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_patientId` ON `visit` (`patientId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `tooth_record` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `visitId` TEXT, `toothFdi` INTEGER NOT NULL, `surface` TEXT, `condition` TEXT NOT NULL, `note` TEXT, `recordedBy` TEXT, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tooth_record_patientId_toothFdi` ON `tooth_record` (`patientId`, `toothFdi`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `treatment_plan` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT, `status` TEXT NOT NULL, `estimatePaise` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatment_plan_patientId` ON `treatment_plan` (`patientId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `plan_item` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `planId` TEXT NOT NULL, `procedureId` TEXT NOT NULL, `toothFdi` INTEGER, `surface` TEXT, `estPaise` INTEGER NOT NULL, `status` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `doneVisitId` TEXT, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_item_planId` ON `plan_item` (`planId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `payment_entry` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `invoiceId` TEXT NOT NULL, `kind` TEXT NOT NULL, `mode` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `reference` TEXT, `idempotencyKey` TEXT NOT NULL, `recordedBy` TEXT, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_entry_invoiceId` ON `payment_entry` (`invoiceId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `prescription` (`id` TEXT NOT NULL, `clinicId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `visitId` TEXT, `dentistId` TEXT, `issuedAt` INTEGER NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_patientId` ON `prescription` (`patientId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `prescription_item` (`id` TEXT NOT NULL, `prescriptionId` TEXT NOT NULL, `drugName` TEXT NOT NULL, `dose` TEXT, `frequency` TEXT, `durationDays` INTEGER, `instructions` TEXT, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_item_prescriptionId` ON `prescription_item` (`prescriptionId`)");
        }
    };

    public static final Migration[] ALL = {MIGRATION_1_2};

    private Migrations() {}
}
