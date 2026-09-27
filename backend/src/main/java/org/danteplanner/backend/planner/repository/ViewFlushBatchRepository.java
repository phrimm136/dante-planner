package org.danteplanner.backend.planner.repository;

import java.util.UUID;

import org.danteplanner.backend.planner.entity.ViewFlushBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewFlushBatchRepository extends JpaRepository<ViewFlushBatch, UUID> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO view_flush_batches (batch_id, applied_at) "
            + "VALUES (:batchId, CURRENT_TIMESTAMP(6))", nativeQuery = true)
    int insertBatch(@Param("batchId") UUID batchId);
}
