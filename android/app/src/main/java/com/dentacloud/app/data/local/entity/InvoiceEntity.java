package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "invoice", indices = {@Index("patientId"), @Index(value = {"clinicId", "invoiceNo"}, unique = true)})
public class InvoiceEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    @NonNull public String invoiceNo = "";
    public long subtotalPaise;
    public long discountPaise;
    public long taxPaise;
    public long totalPaise;
    @NonNull public String status = "ISSUED";
    public long issuedAt;
}
