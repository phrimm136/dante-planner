package org.danteplanner.backend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(name = "planner_title", length = 100)
    private String plannerTitle;

    @Column(name = "comment_snippet", length = 100)
    private String commentSnippet;

    @Column(name = "comment_public_id", columnDefinition = "BINARY(16)")
    private UUID commentPublicId;

    public Notification(Long userId, String contentId, NotificationType notificationType) {
        this.userId = userId;
        this.contentId = contentId;
        this.notificationType = notificationType;
        this.read = false;
    }

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

    public static Notification plannerScoped(
            Long userId,
            String contentId,
            NotificationType notificationType,
            UUID plannerId,
            String plannerTitle
    ) {
        return new Notification(userId, contentId, notificationType, plannerId, plannerTitle, null, null);
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


    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }
}
