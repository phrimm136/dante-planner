package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a user's vote on a planner.
 * Uses composite key (userId, plannerId) to ensure one vote per user per planner.
 *
 * Implements Persistable to handle composite key persistence correctly.
 * JPA's save() uses merge() for entities with composite keys where IDs are set,
 * which doesn't insert new entities properly without this interface.
 */
@Entity
@Table(name = "planner_votes")
@IdClass(PlannerVoteId.class)
public class PlannerVote implements Persistable<PlannerVoteId> {

    /**
     * User ID who cast the vote.
     * IMMUTABILITY EXCEPTION: This field is normally immutable, but can be updated
     * via {@link org.danteplanner.backend.repository.PlannerVoteRepository#reassignUserVotes}
     * during user hard-delete to preserve vote counts while anonymizing the voter.
     * Never modify directly outside of reassignment operations.
     */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "planner_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID plannerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private final VoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    /**
     * No-arg constructor for JPA.
     * Sets voteType to null - should only be used by JPA for entity loading.
     */
    public PlannerVote() {
        this.voteType = null;
    }

    public PlannerVote(Long userId, UUID plannerId, VoteType voteType) {
        this.userId = userId;
        this.plannerId = plannerId;
        this.voteType = voteType; // final field assignment
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
        // Validate entity integrity after loading from database
        if (this.voteType == null) {
            throw new IllegalStateException(
                "Vote loaded with null voteType - data corruption detected for vote: " + getId());
        }
    }

    // Getters only - votes are immutable

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
