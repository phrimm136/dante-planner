package org.danteplanner.backend.shared.outbox.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One observer effect, recorded by the transaction that caused it and applied by a later one.
 *
 * <p>The payload carries ids and nothing else. Everything an effect announces is re-read at
 * dispatch time, so a row that waited out a relay interval cannot announce a value the database no
 * longer holds.</p>
 */
@Entity
@Table(name = "domain_events",
       indexes = {
           @Index(name = "idx_domain_events_undispatched", columnList = "dispatched_at, created_at")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private DomainEventType eventType;

    @Column(name = "aggregate_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID aggregateId;

    @Column(name = "payload", columnDefinition = "JSON", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    private DomainEvent(DomainEventType eventType, UUID aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    /**
     * Build an unrecorded event.
     *
     * @param eventType   the kind of effect
     * @param aggregateId the aggregate the effect is about
     * @param payload     the serialized id-only payload
     * @return the event, carrying no id until it is inserted
     */
    public static DomainEvent of(DomainEventType eventType, UUID aggregateId, String payload) {
        return new DomainEvent(eventType, aggregateId, payload);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Whether the effect has already been applied.
     *
     * @return true once a dispatch marked this row
     */
    public boolean isDispatched() {
        return dispatchedAt != null;
    }

    /**
     * Count one dispatch attempt, whether or not it goes on to succeed.
     */
    public void recordAttempt() {
        this.attempts = this.attempts + 1;
    }

    /**
     * Close the row so no later dispatch derives its effect a second time.
     */
    public void markDispatched() {
        this.dispatchedAt = Instant.now();
    }
}
