package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.PrescriptionEntity;
import com.dentacloud.app.data.local.entity.PrescriptionItemEntity;

import java.util.List;

@Dao
public interface PrescriptionDao {
    @Upsert
    void upsert(PrescriptionEntity rx);

    @Upsert
    void upsertItems(List<PrescriptionItemEntity> items);

    @Query("SELECT * FROM prescription WHERE patientId = :patientId ORDER BY issuedAt DESC")
    LiveData<List<PrescriptionEntity>> forPatient(String patientId);

    @Query("SELECT * FROM prescription WHERE id = :id")
    PrescriptionEntity get(String id);

    @Query("SELECT * FROM prescription_item WHERE prescriptionId = :id")
    List<PrescriptionItemEntity> items(String id);
}
