package org.danteplanner.backend.planner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "planners",
       indexes = {
           @Index(name = "idx_user_modified", columnList = "user_id, last_modified_at DESC"),
           @Index(name = "idx_user_status", columnList = "user_id, status"),
           @Index(name = "idx_user_deleted", columnList = "user_id, deleted_at")
       })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Planner {

    @Id
    @Setter
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Setter
    @Builder.Default
    private String title = "Untitled";

    @Column(nullable = false, length = 50)
    @Setter
    private String category;

    @Column(nullable = false)
    @Setter
    @Convert(converter = PlannerStatusConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private PlannerStatus status = PlannerStatus.DRAFT;

    @Column(columnDefinition = "JSON", nullable = false)
    @Setter
    private String content;

    @Column(name = "schema_version", nullable = false)
    @Setter
    @Builder.Default
    private Integer schemaVersion = 2;

    @Column(name = "content_version", nullable = false)
    @Setter
    private Integer contentVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "planner_type", nullable = false)
    private PlannerType plannerType;

    @Column(name = "sync_version", nullable = false)
    @Builder.Default
    private Long syncVersion = 1L;

    @Column(name = "device_id")
    @Setter
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    @Setter
    private Instant lastModifiedAt;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Publishing fields
    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(nullable = false)
    @Setter
    @Builder.Default
    private Integer upvotes = 0;

    @Column(name = "selected_keywords")
    @Setter
    @Convert(converter = KeywordSetConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    private Set<String> selectedKeywords;

    @Column(name = "view_count", nullable = false, updatable = false)
    @Setter
    @Builder.Default
    private Integer viewCount = 0;

    // Moderation fields
    @Column(name = "hidden_from_recommended", nullable = false)
    @Builder.Default
    private Boolean hiddenFromRecommended = false;

    @Column(name = "hidden_by_moderator_id")
    private Long hiddenByModeratorId;

    @Column(name = "hidden_reason", columnDefinition = "TEXT")
    private String hiddenReason;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    // Written by takeDown(); the preservation guard in PlannerCommandService.upsertPlanner
    // restores a captured value, so this field alone retains a setter (INV7 exception).
    @Column(name = "taken_down_at")
    @Setter
    private Instant takenDownAt;

    @Column(name = "owner_notifications_enabled", nullable = false)
    @Setter
    @Builder.Default
    private Boolean ownerNotificationsEnabled = true;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "recommended_notified_at")
    @Setter
    private Instant recommendedNotifiedAt;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        lastModifiedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedAt = Instant.now();
    }

    /**
     * Check if this planner has been soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Check if this planner has been taken down by a moderator.
     *
     * @return true if planner is taken down, false otherwise
     */
    public boolean isTakenDown() {
        return takenDownAt != null;
    }

    /**
     * Soft delete this planner.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Check if this planner is owned by the given user.
     *
     * @param userId the user ID to check ownership against
     * @return true if this planner belongs to the user, false otherwise
     */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /**
     * Take this planner down as a moderator. Unpublishing is part of the takedown.
     */
    public void takeDown() {
        this.takenDownAt = Instant.now();
        this.published = false;
    }

    /**
     * Hide this planner from the recommended list.
     *
     * @param moderatorId the moderator performing the action
     * @param reason      the reason for hiding
     */
    public void hideFromRecommended(Long moderatorId, String reason) {
        this.hiddenFromRecommended = true;
        this.hiddenByModeratorId = moderatorId;
        this.hiddenReason = reason;
        this.hiddenAt = Instant.now();
    }

    /**
     * Restore this planner to the recommended list.
     */
    public void unhideFromRecommended() {
        this.hiddenFromRecommended = false;
        this.hiddenByModeratorId = null;
        this.hiddenReason = null;
        this.hiddenAt = null;
    }

    /**
     * Toggle the published state. On the first transition to published, stamps
     * firstPublishedAt once.
     *
     * @return the new published state
     * @throws PlannerForbiddenException if publishing a planner taken down by a moderator
     */
    public boolean togglePublished() {
        if (isTakenDown() && !published) {
            throw new PlannerForbiddenException(id);
        }
        boolean nowPublished = !published;
        this.published = nowPublished;
        if (nowPublished && firstPublishedAt == null) {
            this.firstPublishedAt = Instant.now();
        }
        return nowPublished;
    }

    /**
     * Record a save: bump the sync version and stamp the save time.
     */
    public void recordSave() {
        this.syncVersion = this.syncVersion + 1;
        this.savedAt = Instant.now();
    }

    /**
     * Unpublish this planner. No moderation side effects.
     */
    public void unpublish() {
        this.published = false;
    }
}
