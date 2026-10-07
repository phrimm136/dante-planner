package org.danteplanner.backend.service;

import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Every comment write path consults the access guard, whichever entry point the client used.
 *
 * <p>Restrictions are a property of the principal, not of the endpoint, so a path that skips the
 * guard hands a banned user a working write while its sibling rejects them.</p>
 */
@ExtendWith(MockitoExtension.class)
class CommentCommandServiceRestrictionsTest {

    @Mock PlannerCommentRepository commentRepository;
    @Mock PlannerCommentVoteRepository commentVoteRepository;
    @Mock PlannerRepository plannerRepository;
    @Mock PlannerStatsRepository plannerStatsRepository;

    private final FakeUsers users = new FakeUsers();

    private CommentCommandService commentService() {
        return new CommentCommandService(
                commentRepository,
                new CommentQueryService(commentRepository, commentVoteRepository, users,
                        new PlannerAccessGuard(users, plannerRepository),
                        new CommentAccessValidator()),
                null,
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                new CommentAccessValidator(), new CommentAuthorshipValidator(),
                new CommentStateValidator());
    }

    @Test
    void createReply_WhenUserBanned_ThrowsUserBanned() {
        User banned = TestDataFactory.unsavedUser(UserRole.NORMAL);
        banned.setBannedAt(Instant.now());
        users.add(banned);

        assertRejected(() -> commentService()
                .createReply(UUID.randomUUID(), banned.getId(), UUID.randomUUID(), "reply body"))
                .withCode("USER_BANNED");
    }

    @Test
    void createReply_WhenUserTimedOut_ThrowsUserTimedOut() {
        User timedOut = TestDataFactory.unsavedUser(UserRole.NORMAL);
        timedOut.setTimeoutUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        users.add(timedOut);

        assertRejected(() -> commentService()
                .createReply(UUID.randomUUID(), timedOut.getId(), UUID.randomUUID(), "reply body"))
                .withCode("USER_TIMED_OUT");
    }
}
