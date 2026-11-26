package org.danteplanner.backend.shared.outbox.repository;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    @Modifying
    @Query("UPDATE DomainEvent e SET e.dispatchedAt = :at WHERE e.id = :id AND e.dispatchedAt IS NULL")
    int claim(@Param("id") Long id, @Param("at") Instant at);

    @Query(value = """
            SELECT id FROM domain_events
            WHERE dispatched_at IS NULL AND created_at < :cutoff AND attempts < :cap
              AND event_type IN (:types)
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> undispatchedIdsOlderThan(@Param("cutoff") Instant cutoff, @Param("cap") int cap,
            @Param("types") Collection<String> types, @Param("limit") int limit);

    @Modifying
    @Query("UPDATE DomainEvent e SET e.attempts = e.attempts + 1 WHERE e.id = :id")
    int incrementAttempts(@Param("id") Long id);

    @Query("SELECT e.attempts FROM DomainEvent e WHERE e.id = :id")
    Optional<Integer> attemptsOf(@Param("id") Long id);

    default DomainEvent insert(DomainEvent event) {
        Assert.isNull(event.getId(), "insert() takes new rows only");
        return save(event);
    }
}
