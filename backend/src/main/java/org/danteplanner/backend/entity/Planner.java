package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.danteplanner.backend.converter.KeywordSetConverter;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Planner {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private String title = "Untitled";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MDCategory category;

    @Column(nullable = false)
    @Builder.Default
    private String status = "draft";

    @Column(columnDefinition = "JSON", nullable = false)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "sync_version", nullable = false)
    @Builder.Default
    private Long syncVersion = 1L;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
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
    @Builder.Default
    private Integer upvotes = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer downvotes = 0;

    @Column(name = "selected_keywords")
    @Convert(converter = KeywordSetConverter.class)
    private Set<String> selectedKeywords;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

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
     * Soft delete this planner.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
