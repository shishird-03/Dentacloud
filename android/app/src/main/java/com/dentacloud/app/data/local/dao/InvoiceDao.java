package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.InvoiceLineEntity;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;

import java.util.List;

@Dao
public interface InvoiceDao {
    @Upsert
    void upsert(InvoiceEntity invoice);

    @Upsert
    void upsertLines(List<InvoiceLineEntity> lines);

    @Query("SELECT * FROM invoice WHERE patientId = :patientId ORDER BY issuedAt DESC")
    LiveData<List<InvoiceEntity>> forPatient(String patientId);

    @Query("SELECT * FROM invoice WHERE id = :id")
    InvoiceEntity get(String id);

    @Query("SELECT * FROM invoice WHERE id = :id")
    LiveData<InvoiceEntity> observe(String id);

    @Query("SELECT * FROM invoice_line WHERE invoiceId = :invoiceId")
    List<InvoiceLineEntity> lines(String invoiceId);

    @Query("SELECT * FROM invoice_line WHERE invoiceId = :invoiceId")
    LiveData<List<InvoiceLineEntity>> observeLines(String invoiceId);

    @Query("SELECT COUNT(*) FROM invoice WHERE clinicId = :clinicId")
    int countAll(String clinicId);

    /** Ledger entries are append-only: an id that already exists is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertPayment(PaymentEntryEntity p);

    @Query("SELECT * FROM payment_entry WHERE invoiceId = :invoiceId ORDER BY recordedAt")
    LiveData<List<PaymentEntryEntity>> payments(String invoiceId);

    /** Paid so far: payments minus refunds. */
    @Query("SELECT COALESCE(SUM(CASE WHEN kind = 'PAYMENT' THEN amountPaise ELSE -amountPaise END), 0)"
            + " FROM payment_entry WHERE invoiceId = :invoiceId")
    long paid(String invoiceId);

    @Query("SELECT COALESCE(SUM(CASE WHEN kind = 'PAYMENT' THEN amountPaise ELSE -amountPaise END), 0)"
            + " FROM payment_entry WHERE clinicId = :clinicId AND recordedAt >= :since")
    LiveData<Long> collectedSince(String clinicId, long since);

    /** Total still owed across the clinic's open invoices. */
    @Query("SELECT COALESCE(SUM(i.totalPaise), 0) - COALESCE((SELECT SUM(CASE WHEN p.kind = 'PAYMENT' THEN p.amountPaise"
            + " ELSE -p.amountPaise END) FROM payment_entry p JOIN invoice j ON j.id = p.invoiceId"
            + " WHERE j.clinicId = :clinicId AND j.status != 'VOID'), 0)"
            + " FROM invoice i WHERE i.clinicId = :clinicId AND i.status != 'VOID'")
    LiveData<Long> duesTotal(String clinicId);
}
