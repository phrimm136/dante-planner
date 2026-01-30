package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing a moderation action (ban, timeout, role change).
 * Provides immutable audit trail for all moderation decisions.
 */
@Entity
@Table(name = "moderation_actions",
       indexes = {
           @Index(name = "idx_moderation_target_created", columnList = "target_id, created_at"),
           @Index(name = "idx_moderation_actor", columnList = "actor_id"),
           @Index(name = "idx_moderation_type", columnList = "action_type")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "target_uuid", length = 36)
    private String targetUuid;

    @Column(length = 500)
    private String reason;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Types of moderation actions that can be performed.
     */
    public enum ActionType {
        BAN,
        UNBAN,
        TIMEOUT,
        CLEAR_TIMEOUT,
        PROMOTE,
        DEMOTE,
        DELETE_PLANNER,
        DELETE_COMMENT
    }

    /**
     * Types of targets for moderation actions.
     */
    public enum TargetType {
        USER,
        PLANNER,
        COMMENT
    }
}
