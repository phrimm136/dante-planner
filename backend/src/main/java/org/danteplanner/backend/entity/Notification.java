package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing a user notification.
 * Supports notifications for planner milestones, comments, and replies.
 * Uses UNIQUE constraint (user_id, content_id, notification_type) for deduplication.
 */
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notifications_user_read", columnList = "user_id, `read`, created_at DESC"),
           @Index(name = "idx_notifications_created", columnList = "created_at")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_notification_dedup", columnNames = {"user_id", "content_id", "notification_type"})
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "`read`", nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Notification(Long userId, String contentId, NotificationType notificationType) {
        this.userId = userId;
        this.contentId = contentId;
        this.notificationType = notificationType;
        this.read = false;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Business methods

    /**
     * Mark this notification as read.
     */
    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }

    /**
     * Soft delete this notification.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Check if this notification has been soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Set the creation timestamp. Used for testing to create deterministic ordering.
     * Note: createdAt has updatable=false in JPA, but this setter allows test manipulation
     * before the entity is managed or via direct field access in tests.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
