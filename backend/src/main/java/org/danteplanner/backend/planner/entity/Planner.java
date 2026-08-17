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
 * Write-once planner core: identity, owner, type, creation time. Everything mutable
 * lives on the satellite rows ({@link PlannerContent}, {@link PlannerPublication},
 * {@link PlannerModeration}) sharing this row's PK, so FKs pointing here never
 * contend with owner writes.
 *
 * <p>As the aggregate root it coordinates cross-entity invariants (a taken-down
 * planner cannot be republished) and fronts the satellites with delegating readers.</p>
 *
 * <p>Implements Persistable so client-assigned ids take the persist path
 * (batchable INSERTs) instead of merge's SELECT-then-INSERT.</p>
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

    /**
     * Attach the three satellite rows on aggregate creation. Each satellite derives
     * its PK from this root via {@code @MapsId}.
     */
    public void attach(PlannerContent content, PlannerPublication publication, PlannerModeration moderation) {
        content.setPlanner(this);
        publication.setPlanner(this);
        moderation.setPlanner(this);
        this.content = content;
        this.publication = publication;
        this.moderation = moderation;
    }

    // --- delegating readers (aggregate facade) ---

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

    /**
     * The content document as it was read from storage, before any field this
     * transaction applied.
     */
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

    /**
     * The keyword set as it was read from storage, before any field this
     * transaction applied.
     */
    public Set<String> getLoadedKeywords() {
        return content.getLoadedSelectedKeywords();
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
     * Soft delete this planner.
     */
    public void softDelete() {
        content.markDeleted();
    }

    /**
     * Take this planner down as a moderator. Unpublishing is part of the takedown.
     *
     * <p>A takedown that newly stamps the planner reports {@link PublicationChange#WITHDRAWN} even
     * when the planner was already unpublished: the stamp pins it unpublishable, which is a
     * departure from public view the caller still has to persist and project.</p>
     *
     * @return what the takedown turned out to be
     */
    public PublicationChange takeDown() {
        boolean moderated = moderation.takeDown();
        PublicationChange withdrawal = publication.unpublish();
        return moderated ? PublicationChange.WITHDRAWN : withdrawal;
    }

    /**
     * Hide this planner from the recommended list.
     *
     * @param moderatorId the moderator performing the action
     * @param reason      the reason for hiding
     */
    public void hideFromRecommended(Long moderatorId, String reason) {
        moderation.hide(moderatorId, reason);
    }

    /**
     * Restore this planner to the recommended list.
     */
    public void unhideFromRecommended() {
        moderation.unhide();
    }

    /**
     * Update the owner's comment-notification preference.
     */
    public void setOwnerNotificationsEnabled(boolean enabled) {
        publication.setOwnerNotificationsEnabled(enabled);
    }

    /**
     * Publish this planner, stamping firstPublishedAt on the first transition into published.
     *
     * <p>Idempotent: publishing a planner already published changes nothing, so a retry or a
     * failover cannot flip it back.</p>
     *
     * @return what the transition turned out to be
     * @throws PlannerForbiddenException if the planner was taken down by a moderator
     */
    public PublicationChange publish() {
        if (moderation.isTakenDown()) {
            throw new PlannerForbiddenException(id);
        }
        return publication.publish();
    }

    /**
     * Record a save: bump the sync version on the content row.
     */
    public void recordSave() {
        content.recordSave();
    }

    /**
     * Withdraw this planner from public view. No moderation side effects, and a takedown does not
     * block it.
     *
     * <p>Idempotent: withdrawing a planner already withdrawn changes nothing.</p>
     *
     * @return what the transition turned out to be
     */
    public PublicationChange unpublish() {
        return publication.unpublish();
    }
}
