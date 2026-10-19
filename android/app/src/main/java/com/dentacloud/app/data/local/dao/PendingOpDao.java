package com.dentacloud.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.dentacloud.app.data.local.entity.PendingOpEntity;

import java.util.List;

@Dao
public interface PendingOpDao {
    @Insert
    void insert(PendingOpEntity op);

    @Query("SELECT * FROM pending_op ORDER BY createdAt LIMIT :limit")
    List<PendingOpEntity> oldest(int limit);

    /** Ops worth sending: failed ones wait for a human ("Needs attention"). */
    @Query("SELECT * FROM pending_op WHERE lastError IS NULL ORDER BY createdAt LIMIT :limit")
    List<PendingOpEntity> pushable(int limit);

    @Query("SELECT DISTINCT entityId FROM pending_op")
    List<String> pendingEntityIds();

    @Query("DELETE FROM pending_op WHERE id = :id")
    void delete(String id);

    @Query("UPDATE pending_op SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    void markFailed(String id, String error);

    @Query("UPDATE pending_op SET attempts = attempts + 1 WHERE id = :id")
    void markAttempted(String id);

    @Query("SELECT COUNT(*) FROM pending_op")
    LiveData<Integer> count();

    @Query("SELECT COUNT(*) FROM pending_op WHERE lastError IS NOT NULL")
    LiveData<Integer> failedCount();
}
