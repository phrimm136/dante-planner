package org.danteplanner.backend.planner.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Implements Persistable so client-assigned ids take the persist path
 * (batchable INSERTs) instead of merge's SELECT-then-INSERT.
 */
@Entity
@Table(name = "planner")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Planner implements Persistable<UUID> {

    @Id
    @Setter
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "planner_type", nullable = false, updatable = false)
    private PlannerType plannerType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter
    private Instant createdAt;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private PlannerContent content;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private PlannerPublication publication;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private PlannerModeration moderation;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void attach(PlannerContent content, PlannerPublication publication, PlannerModeration moderation) {
        content.setPlanner(this);
        publication.setPlanner(this);
        moderation.setPlanner(this);
        this.content = content;
        this.publication = publication;
        this.moderation = moderation;
    }


    public String getTitle() {
        return content.getTitle();
    }

    public String getCategory() {
        return content.getCategory();
    }

    public PlannerStatus getStatus() {
        return content.getStatus();
    }

    public String getContentJson() {
        return content.getContent();
    }

    public String getLoadedContentJson() {
        return content.getLoadedContent();
    }

    public int getSchemaVersion() {
        return content.getContentSchemaVersion();
    }

    public int getContentVersion() {
        return content.getGameContentVersion();
    }

    public long getSyncVersion() {
        return content.getSyncVersion();
    }

    public UUID getDeviceId() {
        return content.getDeviceId();
    }

    public Instant getLastModifiedAt() {
        return content.getLastModifiedAt();
    }

    public Set<String> getSelectedKeywords() {
        return content.getSelectedKeywords();
    }

    public Set<String> getLoadedKeywords() {
        return content.getLoadedSelectedKeywords();
    }

    public String getLoadedCategory() {
        return content.getLoadedCategory();
    }

    public boolean isPublished() {
        return publication.isPublished();
    }

    public Instant getFirstPublishedAt() {
        return publication.getFirstPublishedAt();
    }

    public boolean isOwnerNotificationsEnabled() {
        return publication.isOwnerNotificationsEnabled();
    }

    public Instant getTakenDownAt() {
        return moderation.getTakenDownAt();
    }

    public boolean isHiddenFromRecommended() {
        return moderation.isHiddenFromRecommended();
    }

    public boolean isDeleted() {
        return content.isDeleted();
    }

    public boolean isTakenDown() {
        return moderation.isTakenDown();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void softDelete() {
        content.markDeleted();
    }

    public PublicationChange takeDown() {
        boolean moderated = moderation.takeDown();
        PublicationChange withdrawal = publication.unpublish();
        return moderated ? PublicationChange.WITHDRAWN : withdrawal;
    }

    public void hideFromRecommended(Long moderatorId, String reason) {
        moderation.hide(moderatorId, reason);
    }

    public void unhideFromRecommended() {
        moderation.unhide();
    }

    public void setOwnerNotificationsEnabled(boolean enabled) {
        publication.setOwnerNotificationsEnabled(enabled);
    }

    public PublicationChange publish() {
        if (moderation.isTakenDown()) {
            throw new PlannerForbiddenException(id);
        }
        return publication.publish();
    }

    public void recordSave() {
        content.recordSave();
    }

    public PublicationChange unpublish() {
        return publication.unpublish();
    }
}
