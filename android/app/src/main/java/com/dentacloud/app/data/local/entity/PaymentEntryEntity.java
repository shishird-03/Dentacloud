package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ledger entry: never edited or deleted. A refund is a new entry, not a change. */
@Entity(tableName = "payment_entry", indices = {@Index("invoiceId")})
public class PaymentEntryEntity {
    public static final String PAYMENT = "PAYMENT", REFUND = "REFUND";
    
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String invoiceId = "";
    @NonNull public String kind = PAYMENT;
    @NonNull public String mode = "CASH";  // CASH, UPI, CARD, BANK
    public long amountPaise;
    public String reference;
    @NonNull public String idempotencyKey = "";
    public String recordedBy;
    public long recordedAt;
}
