package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a permanent account deletion has to do to comment-owned rows.
 *
 * <p>Comments survive their author. Reassigning them to the sentinel anonymizes the writer while
 * leaving the thread readable, which is why this is a reassignment rather than a delete.</p>
 *
 * <p>Demands the caller's transaction, so a failure cannot leave an account anonymized here and
 * intact elsewhere.</p>
 */
@Service
@RequiredArgsConstructor
public class CommentAccountPurgeService {

    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;

    /**
     * Hand the departing account's comments and comment votes to the sentinel.
     *
     * <p>A vote colliding with one the sentinel already cast on the same comment is dropped rather
     * than reassigned, which would duplicate the composite key.</p>
     *
     * @param userId     the departing account
     * @param sentinelId the account that inherits anonymized content
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignAuthorshipToSentinel(Long userId, Long sentinelId) {
        plannerCommentVoteRepository.deleteVotesCollidingWithSentinel(userId, sentinelId);
        plannerCommentVoteRepository.reassignUserVotes(userId, sentinelId);
        plannerCommentRepository.reassignCommentsToSentinel(userId, sentinelId);
    }
}
