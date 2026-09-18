package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.util.Assert;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_votes")
@IdClass(PlannerVoteId.class)
public class PlannerVote implements Persistable<PlannerVoteId> {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "vote_type", nullable = false)
    private final VoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public PlannerVote() {
        this.voteType = null;
    }

    public PlannerVote(Long userId, UUID plannerId, VoteType voteType) {
        this.userId = userId;
        this.plannerId = plannerId;
        this.voteType = voteType;
        this.isNew = true;
    }

    @Override
    public PlannerVoteId getId() {
        return new PlannerVoteId(userId, plannerId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
        Assert.notNull(this.voteType,
                () -> "Vote loaded with null voteType - data corruption detected for vote: " + getId());
    }


    public Long getUserId() {
        return userId;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public VoteType getVoteType() {
        return voteType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
