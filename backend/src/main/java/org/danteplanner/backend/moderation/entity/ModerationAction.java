package org.danteplanner.backend.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;

import java.time.Instant;

@Entity
@Immutable
@EntityListeners(AuditedTimestampListener.class)
@Table(name = "moderation_actions",
       indexes = {
           @Index(name = "idx_moderation_target_created", columnList = "target_id, created_at"),
           @Index(name = "idx_moderation_actor", columnList = "actor_id"),
           @Index(name = "idx_moderation_type", columnList = "action_type")
       })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ModerationActionType actionType;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "target_uuid", length = 36)
    private @Nullable String targetUuid;

    @Column(length = 500)
    private @Nullable String reason;

    @Column(name = "duration_minutes")
    private @Nullable Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ModerationTargetType targetType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
