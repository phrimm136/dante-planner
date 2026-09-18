package org.danteplanner.backend.moderation.repository;

import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /**
     * <p>Ties on {@code createdAt} break by insertion order so the page is stable across reads
     * rather than left to the storage engine.</p>
     */
    @Query("SELECT a FROM ModerationAction a ORDER BY a.createdAt DESC, a.id ASC")
    List<ModerationAction> findRecent(Pageable pageable);

    Optional<ModerationAction> findFirstByTargetUuidAndActionTypeOrderByCreatedAtDesc(
            String targetUuid,
            ModerationAction.ActionType actionType
    );

    /**
     * <p>The actor foreign key is {@code ON DELETE RESTRICT}, so an account that ever moderated
     * cannot be removed while it owns rows here. The rows themselves are the audit record and
     * outlive the account.</p>
     */
    @Modifying
    @Query("UPDATE ModerationAction a SET a.actorId = :sentinelId WHERE a.actorId = :userId")
    int reassignActorToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    default ModerationAction insert(ModerationAction action) {
        Assert.isNull(action.getId(), "insert() takes new rows only");
        return save(action);
    }
}
