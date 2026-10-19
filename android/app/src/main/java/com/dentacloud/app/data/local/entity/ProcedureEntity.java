package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "procedure_catalog")
public class ProcedureEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    public String code;
    @NonNull public String name = "";
    public long defaultPaise;
    public int defaultMinutes = 30;
    public int taxBasisPoints;   // 1800 = 18%

    @NonNull
    @Override
    public String toString() {
        return name; // shown directly by the booking Spinner's ArrayAdapter
    }
}
