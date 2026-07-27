package org.danteplanner.backend.comment.service;

import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CommentAccountPurgeServiceTest {

    private static final Long USER_ID = 123L;
    private static final Long SENTINEL_ID = 0L;

    @Mock private PlannerCommentRepository plannerCommentRepository;
    @Mock private PlannerCommentVoteRepository plannerCommentVoteRepository;

    @InjectMocks private CommentAccountPurgeService purgeService;

    @Test
    void reassignAuthorshipToSentinel_WhenSentinelAlreadyVoted_DeletesCollisionsFirst() {
        purgeService.reassignAuthorshipToSentinel(USER_ID, SENTINEL_ID);

        // Reassigning a vote the sentinel already cast on the same comment would duplicate the
        // composite key, so the collision has to be dropped before the reassignment runs.
        var inOrder = inOrder(plannerCommentVoteRepository, plannerCommentRepository);
        inOrder.verify(plannerCommentVoteRepository).deleteVotesCollidingWithSentinel(USER_ID, SENTINEL_ID);
        inOrder.verify(plannerCommentVoteRepository).reassignUserVotes(USER_ID, SENTINEL_ID);
        inOrder.verify(plannerCommentRepository).reassignCommentsToSentinel(USER_ID, SENTINEL_ID);
    }
}
