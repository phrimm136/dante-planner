package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "public_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID publicId;

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

    // Rich content fields for display and navigation
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(name = "planner_title", length = 100)
    private String plannerTitle;

    @Column(name = "comment_snippet", length = 100)
    private String commentSnippet;

    @Column(name = "comment_public_id", columnDefinition = "BINARY(16)")
    private UUID commentPublicId;

    /**
     * Constructor for PLANNER_RECOMMENDED notifications.
     */
    public Notification(Long userId, String contentId, NotificationType notificationType) {
        this.userId = userId;
        this.contentId = contentId;
        this.notificationType = notificationType;
        this.read = false;
    }

    /**
     * Constructor for comment/reply notifications with rich content.
     *
     * @param userId           the user to notify
     * @param contentId        unique ID for deduplication (comment internal ID)
     * @param notificationType COMMENT_RECEIVED or REPLY_RECEIVED
     * @param plannerId        the planner UUID for navigation
     * @param plannerTitle     the planner title (truncated to 100 chars)
     * @param commentSnippet   snippet of comment content (truncated to 100 chars)
     * @param commentPublicId  the comment's public UUID for anchor link
     */
    public Notification(
            Long userId,
            String contentId,
            NotificationType notificationType,
            UUID plannerId,
            String plannerTitle,
            String commentSnippet,
            UUID commentPublicId
    ) {
        this.userId = userId;
        this.contentId = contentId;
        this.notificationType = notificationType;
        this.plannerId = plannerId;
        this.plannerTitle = truncate(plannerTitle, 100);
        this.commentSnippet = truncate(stripHtml(commentSnippet), 100);
        this.commentPublicId = commentPublicId;
        this.read = false;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private static String stripHtml(String s) {
        if (s == null) return null;
        return s.replaceAll("<[^>]*>", "").trim();
    }

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
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

    /**
     * Set public ID. Used for testing.
     */
    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }
}
