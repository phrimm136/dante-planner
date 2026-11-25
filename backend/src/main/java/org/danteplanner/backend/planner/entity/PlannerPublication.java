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
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
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
    private Planner planner;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "first_published_at")
    private @Nullable Instant firstPublishedAt;

    @Column(name = "owner_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean ownerNotificationsEnabled = true;
}
