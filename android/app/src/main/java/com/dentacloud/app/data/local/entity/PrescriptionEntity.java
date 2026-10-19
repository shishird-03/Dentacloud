package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "prescription", indices = {@Index("patientId")})
public class PrescriptionEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    public String visitId;
    public String dentistId;
    public long issuedAt;
    public String notes;
}
