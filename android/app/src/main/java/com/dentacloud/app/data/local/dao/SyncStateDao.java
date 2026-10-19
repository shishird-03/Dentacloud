package com.dentacloud.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.dentacloud.app.data.local.entity.SyncStateEntity;

@Dao
public interface SyncStateDao {
    @Upsert
    void upsert(SyncStateEntity s);

    @Query("SELECT * FROM sync_state WHERE clinicId = :clinicId")
    SyncStateEntity get(String clinicId);
}
