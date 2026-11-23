package org.danteplanner.backend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.notification.api.NotificationType;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditedTimestampListener.class)
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

    private static final int MAX_TEXT_LENGTH = 100;

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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private @Nullable Instant readAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private @Nullable UUID plannerId;

    @Column(name = "planner_title", length = 100)
    private @Nullable String plannerTitle;

    @Column(name = "comment_snippet", length = 100)
    private @Nullable String commentSnippet;

    @Column(name = "comment_public_id", columnDefinition = "BINARY(16)")
    private @Nullable UUID commentPublicId;

    public sealed interface Content {

        record Plain() implements Content {
        }

        record PlannerScoped(UUID plannerId, String plannerTitle) implements Content {
        }

        record CommentScoped(UUID plannerId, String plannerTitle, String snippet, UUID commentPublicId)
                implements Content {
        }
    }

    public Notification(Long userId, String contentId, NotificationType notificationType) {
        this(userId, contentId, notificationType, new Content.Plain());
    }

    public Notification(Long userId, String contentId, NotificationType notificationType, Content content) {
        this.userId = userId;
        this.contentId = contentId;
        this.notificationType = notificationType;
        this.read = false;
        switch (content) {
            case Content.Plain plain -> { }
            case Content.PlannerScoped planner -> {
                this.plannerId = planner.plannerId();
                this.plannerTitle = truncate(planner.plannerTitle(), MAX_TEXT_LENGTH);
            }
            case Content.CommentScoped comment -> {
                this.plannerId = comment.plannerId();
                this.plannerTitle = truncate(comment.plannerTitle(), MAX_TEXT_LENGTH);
                this.commentSnippet = truncate(stripHtml(comment.snippet()), MAX_TEXT_LENGTH);
                this.commentPublicId = comment.commentPublicId();
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private static String stripHtml(String s) {
        return s.replaceAll("<[^>]*>", "").trim();
    }

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
    }


    public void markAsRead(Instant now) {
        if (!this.read) {
            this.read = true;
            this.readAt = now;
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
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
