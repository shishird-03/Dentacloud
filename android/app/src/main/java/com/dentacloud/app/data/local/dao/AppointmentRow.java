package com.dentacloud.app.data.local.dao;

import androidx.room.Embedded;

import com.dentacloud.app.data.local.entity.AppointmentEntity;

import java.util.Objects;

/** Appointment joined with the display fields the lists need. */
public class AppointmentRow {
    @Embedded public AppointmentEntity appt;
    public String patientName;
    public String patientPhone;
    public String procedureName;

    public boolean sameContent(AppointmentRow o) {
        return appt.updatedAt == o.appt.updatedAt && appt.status.equals(o.appt.status)
                && appt.colorLabel == o.appt.colorLabel && Objects.equals(patientName, o.patientName);
    }
}
