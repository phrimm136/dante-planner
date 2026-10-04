package org.danteplanner.backend.comment.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.user.api.AccountLifecycleParticipant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a permanent account deletion has to do to comment-owned rows.
 */
@Service
@RequiredArgsConstructor
public class CommentAccountPurgeService implements AccountLifecycleParticipant {

    private final PlannerCommentRepository plannerCommentRepository;
    private final PlannerCommentVoteRepository plannerCommentVoteRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignToSentinel(Long userId, Long sentinelId) {
        plannerCommentVoteRepository.deleteVotesCollidingWithSentinel(userId, sentinelId);
        plannerCommentVoteRepository.reassignUserVotes(userId, sentinelId);
        plannerCommentRepository.anonymizeCommentsToSentinel(userId, sentinelId);
    }
}
