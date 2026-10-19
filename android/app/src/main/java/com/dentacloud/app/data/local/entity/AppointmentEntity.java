package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "appointment", indices = {@Index({"clinicId", "startAt"}), @Index("patientId"), @Index("dentistId")})
public class AppointmentEntity {
    public static final String BOOKED = "BOOKED", ARRIVED = "ARRIVED", IN_CHAIR = "IN_CHAIR",
            DONE = "DONE", NO_SHOW = "NO_SHOW", CANCELLED = "CANCELLED";

    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientId = "";
    @NonNull public String dentistId = "";
    public String chairId;
    public String procedureId;
    public long startAt;
    public long endAt;
    @NonNull public String status = BOOKED;
    public String reason;
    public int colorLabel;
    public long updatedAt;
    public int version;

    public boolean isActive() {
        return !CANCELLED.equals(status) && !NO_SHOW.equals(status);
    }
}
