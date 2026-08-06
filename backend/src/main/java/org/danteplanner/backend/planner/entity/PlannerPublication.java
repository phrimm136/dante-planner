package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner publication lifecycle: the published toggle, the first-publish stamp that
 * anchors the catalog's recency sort, and the owner's comment-notification preference.
 */
@Entity
@Table(name = "planner_publication")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerPublication {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "planner_id")
    @Setter
    private Planner planner;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "first_published_at")
    @Setter
    private Instant firstPublishedAt;

    @Column(name = "owner_notifications_enabled", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    @Builder.Default
    private Boolean ownerNotificationsEnabled = true;

    /**
     * Enter public view, stamping firstPublishedAt on the first entry.
     *
     * <p>Idempotent by construction rather than by the caller checking first: publishing a planner
     * already published changes nothing, so a retry or a failover cannot flip it back.</p>
     *
     * @return what the transition turned out to be
     */
    PublicationChange publish() {
        if (published) {
            return PublicationChange.NONE;
        }
        this.published = true;

        if (firstPublishedAt == null) {
            this.firstPublishedAt = Instant.now();
            return PublicationChange.FIRST_PUBLISH;
        }
        return PublicationChange.REPUBLISH;
    }

    /**
     * Leave public view, keeping the first-publish stamp that anchors the catalog's recency sort.
     *
     * <p>Idempotent by construction, for the same reason {@link #publish()} is.</p>
     *
     * @return what the transition turned out to be
     */
    PublicationChange unpublish() {
        if (!published) {
            return PublicationChange.NONE;
        }
        this.published = false;
        return PublicationChange.WITHDRAWN;
    }
}
