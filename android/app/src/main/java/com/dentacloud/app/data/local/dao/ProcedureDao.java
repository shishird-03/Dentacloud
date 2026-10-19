package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.ProcedureEntity;

import java.util.List;

@Dao
public interface ProcedureDao {
    @Upsert
    void upsertAll(List<ProcedureEntity> list);

    @Query("SELECT * FROM procedure_catalog WHERE clinicId = :clinicId ORDER BY name")
    LiveData<List<ProcedureEntity>> all(String clinicId);

    @Query("SELECT * FROM procedure_catalog WHERE id = :id")
    ProcedureEntity get(String id);

    @Query("SELECT COUNT(*) FROM procedure_catalog WHERE clinicId = :clinicId")
    int count(String clinicId);
}
