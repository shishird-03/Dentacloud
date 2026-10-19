package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "plan_item", indices = {@Index("planId")})
public class PlanItemEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String planId = "";
    @NonNull public String procedureId = "";
    public Integer toothFdi;
    public String surface;
    public long estPaise;
    @NonNull public String status = "PLANNED";
    public int sequence;
    public String doneVisitId;
}
