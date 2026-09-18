package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a permanent account deletion has to do to comment-owned rows.
 */
@Service
@RequiredArgsConstructor
public class CommentAccountPurgeService {

    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignAuthorshipToSentinel(Long userId, Long sentinelId) {
        plannerCommentVoteRepository.deleteVotesCollidingWithSentinel(userId, sentinelId);
        plannerCommentVoteRepository.reassignUserVotes(userId, sentinelId);
        plannerCommentRepository.anonymizeCommentsToSentinel(userId, sentinelId);
    }
}
