package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_state")
public class SyncStateEntity {
    @PrimaryKey @NonNull public String clinicId = "";
    public long lastSeq;
    public long lastSyncedAt;
}
