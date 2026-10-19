package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Append-only chart history (PRD 6): the current chart is the latest record per tooth surface. */
@Entity(tableName = "tooth_record", indices = {@Index({"patientId", "toothFdi"})})
public class ToothRecordEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    public String visitId;
    public int toothFdi;
    public String surface;     // M, O, D, B, L or null for the whole tooth
    @NonNull public String condition = "";
    public String note;
    public String recordedBy;
    public long recordedAt;
}
