package org.danteplanner.backend.service;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.service.CommentService;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.danteplanner.backend.notification.service.NotificationService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserTimedOutException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Every comment write path consults the access guard, whichever entry point the client used.
 *
 * <p>Restrictions are a property of the principal, not of the endpoint, so a path that skips the
 * guard hands a banned user a working write while its sibling rejects them.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceRestrictionsTest {

    @Mock PlannerCommentRepository commentRepository;
    @Mock PlannerCommentVoteRepository commentVoteRepository;
    @Mock PlannerRepository plannerRepository;
    @Mock PlannerStatsRepository plannerStatsRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock PlannerCommentSseService plannerCommentSseService;
    @Mock SsePublisher ssePublisher;

    private CommentService commentService() {
        return new CommentService(
                commentRepository, commentVoteRepository, plannerRepository,
                plannerStatsRepository, userRepository, notificationService,
                plannerCommentSseService, ssePublisher,
                new PlannerAccessGuard(userRepository, plannerRepository));
    }

    private User restrictedUser() {
        return User.builder()
                .id(7L)
                .email("restricted@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-restricted")
                .usernameEpithet("TEST")
                .usernameSuffix("res01")
                .role(UserRole.NORMAL)
                .build();
    }

    @Test
    @DisplayName("a banned user cannot reply to a comment")
    void createReply_WhenUserBanned_ThrowsUserBanned() {
        User banned = restrictedUser();
        banned.setBannedAt(Instant.now());
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(banned));

        assertThrows(UserBannedException.class, () -> commentService()
                .createReply(UUID.randomUUID(), banned.getId(), UUID.randomUUID(), "reply body"));
    }

    @Test
    @DisplayName("a timed-out user cannot reply to a comment")
    void createReply_WhenUserTimedOut_ThrowsUserTimedOut() {
        User timedOut = restrictedUser();
        timedOut.setTimeoutUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(timedOut));

        assertThrows(UserTimedOutException.class, () -> commentService()
                .createReply(UUID.randomUUID(), timedOut.getId(), UUID.randomUUID(), "reply body"));
    }
}
