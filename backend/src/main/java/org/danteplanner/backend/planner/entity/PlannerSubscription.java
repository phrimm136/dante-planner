package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "planner_subscriptions")
@IdClass(PlannerSubscriptionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerSubscription {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID plannerId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
