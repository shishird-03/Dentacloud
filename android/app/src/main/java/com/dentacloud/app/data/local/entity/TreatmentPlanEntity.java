package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "treatment_plan", indices = {@Index("patientId")})
public class TreatmentPlanEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    public String title;
    @NonNull public String status = "PROPOSED";
    public long estimatePaise;
    public long createdAt;
    public long updatedAt;
}
