package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.PatientEntity;

import java.util.List;

@Dao
public interface PatientDao {
    @Upsert
    void upsert(PatientEntity p);

    /** Name contains, phone contains, or exact patient number (Part B #5: search by ID, then call). */
    @Query("SELECT * FROM patient WHERE clinicId = :clinicId AND deletedAt IS NULL AND ("
            + ":q = '' OR fullName LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR patientNo = :q)"
            + " ORDER BY fullName COLLATE NOCASE LIMIT 200")
    LiveData<List<PatientEntity>> search(String clinicId, String q);

    @Query("SELECT * FROM patient WHERE id = :id")
    LiveData<PatientEntity> observe(String id);

    @Query("SELECT * FROM patient WHERE id = :id")
    PatientEntity get(String id);

    @Query("SELECT * FROM patient WHERE clinicId = :clinicId AND phone = :phone AND deletedAt IS NULL AND id != :excludeId")
    List<PatientEntity> withPhone(String clinicId, String phone, String excludeId);

    @Query("SELECT * FROM patient WHERE clinicId = :clinicId AND deletedAt IS NULL ORDER BY patientNo")
    List<PatientEntity> allNow(String clinicId);

    @Query("SELECT COUNT(*) FROM patient WHERE clinicId = :clinicId")
    int countAll(String clinicId);

    @Query("SELECT COUNT(*) FROM patient WHERE clinicId = :clinicId AND deletedAt IS NULL AND createdAt >= :since")
    LiveData<Integer> countNewSince(String clinicId, long since);
}
