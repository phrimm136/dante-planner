package org.danteplanner.backend.planner.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
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
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.shared.failure.Parsed;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Implements Persistable so client-assigned ids take the persist path
 * (batchable INSERTs) instead of merge's SELECT-then-INSERT.
 */
@Entity
@EntityListeners(AuditedTimestampListener.class)
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

    @Setter
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "planner_type", nullable = false, updatable = false)
    private PlannerType plannerType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private PlannerContent content;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.PERSIST, optional = false)
    private PlannerPublication publication;

    @OneToOne(mappedBy = "planner", cascade = CascadeType.PERSIST, optional = false)
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

    public void attach(PlannerContent content, PlannerPublication publication, PlannerModeration moderation) {
        content.setPlanner(this);
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

    public @Nullable UUID getDeviceId() {
        return content.getDeviceId();
    }

    public Instant getLastModifiedAt() {
        return content.getLastModifiedAt();
    }

    public Set<String> getSelectedKeywords() {
        return orEmpty(content.getSelectedKeywords());
    }

    public Set<String> getLoadedKeywords() {
        return orEmpty(content.getLoadedSelectedKeywords());
    }

    private static Set<String> orEmpty(@Nullable Set<String> column) {
        return column == null ? Set.of() : column;
    }

    public String getLoadedCategory() {
        return content.getLoadedCategory();
    }

    public boolean isPublished() {
        return publication.isPublished();
    }

    public @Nullable Instant getFirstPublishedAt() {
        return publication.getFirstPublishedAt();
    }

    public boolean isOwnerNotificationsEnabled() {
        return publication.isOwnerNotificationsEnabled();
    }

    public @Nullable Instant getTakenDownAt() {
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
        return this.userId.equals(userId);
    }

    public void softDelete(Instant now) {
        content.markDeleted(now);
    }

    public Publication publicationState() {
        return Publication.of(this);
    }

    public Listing listing() {
        return Listing.of(moderation);
    }

    public Publishable requirePublishable() {
        return Publishable.parse(this);
    }

    @Parsed
    public record Publishable(Publication publication) {

        private static Publishable parse(Planner planner) {
            if (planner.isTakenDown()) {
                throw new PlannerForbiddenException(planner.id);
            }
            return new Publishable(planner.publicationState());
        }
    }

    public void recordSave() {
        content.recordSave();
    }
}
