package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "prescription_item", indices = {@Index("prescriptionId")})
public class PrescriptionItemEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String prescriptionId = "";
    @NonNull public String drugName = "";
    public String dose;
    public String frequency;
    public Integer durationDays;
    public String instructions;
}
