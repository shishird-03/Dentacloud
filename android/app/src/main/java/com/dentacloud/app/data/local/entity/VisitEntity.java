package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "visit", indices = {@Index("patientId")})
public class VisitEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    public String appointmentId;
    public String dentistId;
    public long visitedAt;
    public String complaint;
    public String diagnosis;
    public String notes;
    public long updatedAt;
    public int version;
}
