package org.danteplanner.backend.shared.outbox.repository;

import jakarta.persistence.LockModeType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the domain event outbox.
 */
public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    /**
     * Load one event under a row lock, so a concurrent dispatch of the same id waits rather than
     * deriving the effect alongside it.
     *
     * @param id the event id
     * @return the locked event, empty when no row carries the id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DomainEvent e WHERE e.id = :id")
    Optional<DomainEvent> findForDispatch(@Param("id") Long id);

    /**
     * The oldest events that no dispatch has closed, skipping those young enough that the eager hop
     * may still be working on them and those that have spent their attempts.
     *
     * @param cutoff the age an event must exceed to be relayed
     * @param cap    the attempt count at which an event stops being retried
     * @param limit  the most ids to return in one pass
     * @return the ids, oldest first
     */
    @Query(value = """
            SELECT id FROM domain_events
            WHERE dispatched_at IS NULL AND created_at < :cutoff AND attempts < :cap
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> undispatchedIdsOlderThan(@Param("cutoff") Instant cutoff, @Param("cap") int cap,
            @Param("limit") int limit);

    /**
     * Count one dispatch attempt against an event.
     *
     * @param id the event
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE DomainEvent e SET e.attempts = e.attempts + 1 WHERE e.id = :id")
    int incrementAttempts(@Param("id") Long id);

    /**
     * The attempts an event has spent.
     *
     * @param id the event
     * @return the count, empty when no row carries the id
     */
    @Query("SELECT e.attempts FROM DomainEvent e WHERE e.id = :id")
    Optional<Integer> attemptsOf(@Param("id") Long id);

    /**
     * Persists an event that does not exist yet.
     *
     * @param event the event to insert, carrying no id
     * @return the persisted event, carrying its generated id
     * @throws IllegalArgumentException if the event already carries an id
     */
    default DomainEvent insert(DomainEvent event) {
        Assert.isNull(event.getId(), "insert() takes new rows only");
        return save(event);
    }
}
