package org.danteplanner.backend.controller;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.comment.controller.CommentController;
import org.danteplanner.backend.shared.dto.ToggleNotificationRequest;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.moderation.controller.ModerationController;
import org.danteplanner.backend.moderation.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.service.CommentModerationService;
import org.danteplanner.backend.moderation.service.ModerationQueryService;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Mutating endpoints consult the rate limiter before they do the work.
 *
 * <p>The order matters as much as the call: a limiter consulted after the mutation limits nothing,
 * so each case pins both.</p>
 */
@ExtendWith(MockitoExtension.class)
class RateLimitedMutationTest {

    private static final Long ACTOR_ID = 7L;

    @Mock private CommentQueryService commentQueryService;
    @Mock private CommentCommandService commentCommandService;
    @Mock private CommentEngagementService commentEngagementService;
    @Mock private UserModerationService userModerationService;
    @Mock private PlannerModerationService plannerModerationService;
    @Mock private CommentModerationService commentModerationService;
    @Mock private ModerationQueryService moderationQueryService;
    @Mock private RateLimitService rateLimitService;

    private CommentController commentController;
    private ModerationController moderationController;

    @BeforeEach
    void setUp() {
        commentController = new CommentController(commentQueryService, commentCommandService,
                commentEngagementService, rateLimitService);
        moderationController = new ModerationController(userModerationService, plannerModerationService,
                commentModerationService, moderationQueryService, rateLimitService);
    }

    @Test
    void deleteComment_WhenInvoked_ChecksTheCommentLimitFirst() {
        UUID commentId = UUID.randomUUID();

        commentController.deleteComment(ACTOR_ID, commentId);

        InOrder order = inOrder(rateLimitService, commentCommandService);
        order.verify(rateLimitService).check(RateLimitPolicy.COMMENT, ACTOR_ID);
        order.verify(commentCommandService).deleteComment(commentId, ACTOR_ID);
    }

    @Test
    void toggleNotification_WhenInvoked_ChecksTheCommentLimitFirst() {
        UUID commentId = UUID.randomUUID();
        when(commentEngagementService.toggleNotification(commentId, ACTOR_ID, true))
                .thenReturn(new ToggleNotificationResponse(true));

        commentController.toggleNotification(ACTOR_ID, commentId, new ToggleNotificationRequest(true));

        InOrder order = inOrder(rateLimitService, commentEngagementService);
        order.verify(rateLimitService).check(RateLimitPolicy.COMMENT, ACTOR_ID);
        order.verify(commentEngagementService).toggleNotification(commentId, ACTOR_ID, true);
    }

    @Test
    void reportComment_WhenInvoked_ChecksTheReportLimitFirst() {
        UUID commentId = UUID.randomUUID();
        CommentReportRequest request = new CommentReportRequest("SPAM");
        when(commentEngagementService.reportComment(commentId, ACTOR_ID, request))
                .thenReturn(new CommentReportResponse(Instant.EPOCH));

        commentController.reportComment(ACTOR_ID, commentId, request);

        InOrder order = inOrder(rateLimitService, commentEngagementService);
        order.verify(rateLimitService).check(RateLimitPolicy.REPORT, ACTOR_ID);
        order.verify(commentEngagementService).reportComment(commentId, ACTOR_ID, request);
    }

    @Test
    void unpublishPlanner_WhenInvoked_ChecksTheModerationLimitFirst() {
        User owner = User.builder()
                .id(1L)
                .email("owner@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-1")
                .usernameEpithet("W_CORP")
                .usernameSuffix("own01")
                .build();
        Planner planner = TestDataFactory.planner(owner).published(false).build();
        when(plannerModerationService.unpublishPlanner(any(), any())).thenReturn(planner);

        moderationController.unpublishPlanner(ACTOR_ID, planner.getId());

        InOrder order = inOrder(rateLimitService, plannerModerationService);
        order.verify(rateLimitService).check(RateLimitPolicy.MODERATION, ACTOR_ID);
        order.verify(plannerModerationService).unpublishPlanner(ACTOR_ID, planner.getId());
    }
}
