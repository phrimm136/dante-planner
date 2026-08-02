package org.danteplanner.backend.moderation.repository;

import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /**
     * Read the newest audit records, newest first, bounded by the requested page.
     *
     * <p>Ties on {@code createdAt} break by insertion order so the page is stable across reads
     * rather than left to the storage engine.</p>
     *
     * @param pageable the page to read
     * @return the requested page of records, newest first
     */
    @Query("SELECT a FROM ModerationAction a ORDER BY a.createdAt DESC, a.id ASC")
    List<ModerationAction> findRecent(Pageable pageable);

    /**
     * Find the most recent moderation action of a specific type for a target.
     * Used to retrieve ban/timeout reasons for display to users.
     *
     * @param targetUuid the target's public UUID
     * @param actionType the type of action to find
     * @return the most recent action if found
     */
    Optional<ModerationAction> findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(
            String targetUuid,
            ModerationAction.ActionType actionType
    );

    /**
     * Reassign the actor of every recorded action to the sentinel account.
     *
     * <p>The actor foreign key is {@code ON DELETE RESTRICT}, so an account that ever moderated
     * cannot be removed while it owns rows here. The rows themselves are the audit record and
     * outlive the account.</p>
     *
     * @param userId     the departing account
     * @param sentinelId the account that inherits the actions
     * @return the number of actions reassigned
     */
    @Modifying
    @Query("UPDATE ModerationAction a SET a.actorId = :sentinelId WHERE a.actorId = :userId")
    int reassignActorToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
