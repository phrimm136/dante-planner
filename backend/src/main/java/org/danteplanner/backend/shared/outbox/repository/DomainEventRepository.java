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

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DomainEvent e WHERE e.id = :id")
    Optional<DomainEvent> findForDispatch(@Param("id") Long id);

    @Query(value = """
            SELECT id FROM domain_events
            WHERE dispatched_at IS NULL AND created_at < :cutoff AND attempts < :cap
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> undispatchedIdsOlderThan(@Param("cutoff") Instant cutoff, @Param("cap") int cap,
            @Param("limit") int limit);

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
