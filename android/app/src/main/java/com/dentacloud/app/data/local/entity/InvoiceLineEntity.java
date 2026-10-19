package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "invoice_line", indices = @Index("invoiceId"),
        foreignKeys = @ForeignKey(entity = InvoiceEntity.class, parentColumns = "id", childColumns = "invoiceId"))
public class InvoiceLineEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String invoiceId = "";
    public String planItemId;
    @NonNull public String description = "";
    public int qty = 1;
    public long amountPaise;
    public int taxBasisPoints;
}
