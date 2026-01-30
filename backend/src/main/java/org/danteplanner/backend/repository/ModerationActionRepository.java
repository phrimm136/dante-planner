package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.ModerationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

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
}
