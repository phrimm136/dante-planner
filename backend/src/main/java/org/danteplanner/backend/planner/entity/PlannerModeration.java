package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Moderator-only planner state: takedown and the hidden-from-recommended audit trail.
 * A row with {@code hiddenAt} set but a NULL {@code hiddenByModeratorId} means the
 * moderator account was deleted (FK ON DELETE SET NULL).
 */
@Entity
@Table(name = "planner_moderation")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerModeration {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "planner_id")
    @Setter
    private Planner planner;

    @Column(name = "taken_down_at")
    @Setter
    private Instant takenDownAt;

    @Column(name = "hidden_from_recommended", nullable = false)
    @Builder.Default
    private Boolean hiddenFromRecommended = false;

    @Column(name = "hidden_by_moderator_id")
    private Long hiddenByModeratorId;

    @Column(name = "hidden_reason", columnDefinition = "TEXT")
    private String hiddenReason;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    public boolean isTakenDown() {
        return takenDownAt != null;
    }

    public void takeDown() {
        this.takenDownAt = Instant.now();
    }

    public void hide(Long moderatorId, String reason) {
        this.hiddenFromRecommended = true;
        this.hiddenByModeratorId = moderatorId;
        this.hiddenReason = reason;
        this.hiddenAt = Instant.now();
    }

    public void unhide() {
        this.hiddenFromRecommended = false;
        this.hiddenByModeratorId = null;
        this.hiddenReason = null;
        this.hiddenAt = null;
    }
}
