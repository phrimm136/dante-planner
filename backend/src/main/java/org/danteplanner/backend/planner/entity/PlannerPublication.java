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
     * Drive the published state to {@code target}, stamping firstPublishedAt on the first
     * transition into published.
     *
     * <p>Idempotent by construction rather than by the caller checking first: applying the state
     * a planner already holds changes nothing, so a retry or a failover cannot flip it back.</p>
     *
     * @param target the desired published state
     * @return what the transition turned out to be
     */
    PublicationChange setPublished(boolean target) {
        if (published == target) {
            return PublicationChange.NONE;
        }
        this.published = target;

        if (!target) {
            return PublicationChange.WITHDRAWN;
        }
        if (firstPublishedAt == null) {
            this.firstPublishedAt = Instant.now();
            return PublicationChange.FIRST_PUBLISH;
        }
        return PublicationChange.REPUBLISH;
    }

    PublicationChange unpublish() {
        return setPublished(false);
    }
}
