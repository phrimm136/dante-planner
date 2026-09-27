package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "view_flush_batches")
@Getter
@NoArgsConstructor
public class ViewFlushBatch {

    @Id
    @Column(name = "batch_id", columnDefinition = "BINARY(16)")
    private UUID batchId;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;
}
