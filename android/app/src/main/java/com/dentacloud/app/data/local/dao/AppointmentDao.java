package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.AppointmentEntity;

import java.util.List;

@Dao
public interface AppointmentDao {
    String ROW = "SELECT a.*, p.fullName AS patientName, p.phone AS patientPhone, pc.name AS procedureName"
            + " FROM appointment a JOIN patient p ON p.id = a.patientId"
            + " LEFT JOIN procedure_catalog pc ON pc.id = a.procedureId";

    @Upsert
    void upsert(AppointmentEntity a);

    @Query(ROW + " WHERE a.clinicId = :clinicId AND a.startAt >= :from AND a.startAt < :to ORDER BY a.startAt")
    LiveData<List<AppointmentRow>> between(String clinicId, long from, long to);

    @Query(ROW + " WHERE a.patientId = :patientId ORDER BY a.startAt DESC")
    LiveData<List<AppointmentRow>> forPatient(String patientId);

    @Query(ROW + " WHERE a.id = :id")
    LiveData<AppointmentRow> observe(String id);

    @Query("SELECT * FROM appointment WHERE id = :id")
    AppointmentEntity get(String id);

    /** Local mirror of the server's exclusion constraint; the server stays the final judge. */
    @Query("SELECT COUNT(*) FROM appointment WHERE dentistId = :dentistId AND id != :excludeId"
            + " AND status NOT IN ('CANCELLED','NO_SHOW') AND startAt < :end AND endAt > :start")
    int countOverlaps(String dentistId, long start, long end, String excludeId);

    @Query("SELECT * FROM appointment WHERE clinicId = :clinicId AND startAt >= :from AND startAt < :to"
            + " AND status = 'BOOKED' ORDER BY startAt")
    List<AppointmentEntity> bookedBetween(String clinicId, long from, long to);
}
