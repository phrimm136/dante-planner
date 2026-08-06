package org.danteplanner.backend.architecture;

import java.util.List;

import org.danteplanner.backend.shared.ratelimit.RateLimited;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes which policy each handler is charged against.
 *
 * <p>The coverage rule proves every handler declares <em>something</em> and the shape rule proves
 * the declaration is well-formed. Neither notices a handler pointed at the wrong policy: swapping
 * COMMENT for CRUD, or renaming an endpoint key, leaves both rules green while the limit changes
 * and a live bucket is abandoned under its old key.</p>
 *
 * <p>So the mapping itself is pinned, in the same spirit as {@code RateLimitKeyFormatTest} pins the
 * key spellings. Retargeting a handler is legitimate; doing it invisibly is not. The table below is
 * the deliberate diff that makes it visible, and it covers exemptions too — an endpoint quietly
 * losing its limit shows up here as a changed line.</p>
 */
class RateLimitBindingFreezeTest {

    private static final List<String> FROZEN_BINDINGS = List.of(
            "AdminController.changeRole -> EXEMPT",
            "AdminController.getUserRole -> EXEMPT",
            "AdminModerationController.hideFromRecommended -> EXEMPT",
            "AdminModerationController.unhideFromRecommended -> EXEMPT",
            "AuthController.appleCallback -> AUTH (RESPOND)",
            "AuthController.getCurrentUser -> EXEMPT",
            "AuthController.googleCallback -> AUTH (REDIRECT_LOGIN)",
            "AuthController.googleStart -> EXEMPT",
            "AuthController.logout -> EXEMPT",
            "AuthController.logoutAll -> EXEMPT",
            "CommentController.createComment -> COMMENT (RESPOND)",
            "CommentController.createReply -> COMMENT (RESPOND)",
            "CommentController.deleteComment -> COMMENT (RESPOND)",
            "CommentController.getComments -> EXEMPT",
            "CommentController.reportComment -> REPORT (RESPOND)",
            "CommentController.toggleNotification -> COMMENT (RESPOND)",
            "CommentController.toggleUpvote -> COMMENT (RESPOND)",
            "CommentController.updateComment -> COMMENT (RESPOND)",
            "ModerationController.banUser -> MODERATION (RESPOND)",
            "ModerationController.deleteComment -> MODERATION (RESPOND)",
            "ModerationController.getAllUsers -> EXEMPT",
            "ModerationController.getModerationActions -> EXEMPT",
            "ModerationController.getTimedOutUsers -> EXEMPT",
            "ModerationController.removeTimeout -> MODERATION (RESPOND)",
            "ModerationController.takedownPlanner -> MODERATION (RESPOND)",
            "ModerationController.timeoutUser -> MODERATION (RESPOND)",
            "ModerationController.unbanUser -> MODERATION (RESPOND)",
            "ModerationController.unpublishPlanner -> MODERATION (RESPOND)",
            "NotificationController.deleteAllNotifications -> CRUD:notifications-delete-all (RESPOND)",
            "NotificationController.deleteNotification -> CRUD:notifications-delete (RESPOND)",
            "NotificationController.getInbox -> CRUD:notifications-inbox (RESPOND)",
            "NotificationController.getUnreadCount -> CRUD:notifications-unread-count (RESPOND)",
            "NotificationController.markAllAsRead -> CRUD:notifications-mark-all-read (RESPOND)",
            "NotificationController.markAsRead -> CRUD:notifications-mark-read (RESPOND)",
            "PlannerCommandController.deletePlanner -> CRUD:delete (RESPOND)",
            "PlannerCommandController.importPlanners -> IMPORT (RESPOND)",
            "PlannerCommandController.upsertPlanner -> CRUD:upsert (RESPOND)",
            "PlannerCommentSseController.subscribeToComments -> PLANNER_COMMENT_SSE (RESPOND)",
            "PlannerController.getConfig -> PUBLIC_READ (RESPOND)",
            "PlannerEngagementController.castUpvote -> CRUD:vote (RESPOND)",
            "PlannerEngagementController.setBookmark -> CRUD:bookmark (RESPOND)",
            "PlannerEngagementController.submitReport -> REPORT (RESPOND)",
            "PlannerEngagementController.toggleSubscription -> CRUD:subscribe (RESPOND)",
            "PlannerPublishingController.setPublished -> CRUD:publish (RESPOND)",
            "PlannerPublishingController.toggleOwnerNotifications -> CRUD:notifications-toggle (RESPOND)",
            "PlannerQueryController.getPlanner -> CRUD:get (RESPOND)",
            "PlannerQueryController.getPlanners -> CRUD:list (RESPOND)",
            "PlannerSseController.subscribeToEvents -> SSE (RESPOND)",
            "PublishedPlannerController.getPublishedPlanner -> PUBLIC_READ (RESPOND)",
            "PublishedPlannerController.getPublishedPlanners -> PUBLIC_READ (RESPOND)",
            "PublishedPlannerController.getRecommendedPlanners -> PUBLIC_READ (RESPOND)",
            "SseController.subscribe -> SSE (RESPOND)",
            "UserController.deleteMyAccount -> CRUD:user-delete (RESPOND)",
            "UserController.getEpithets -> EXEMPT",
            "UserController.getSettings -> EXEMPT",
            "UserController.updateSettings -> CRUD:user-settings-update (RESPOND)",
            "UserController.updateUsernameEpithet -> CRUD:user-epithet-update (RESPOND)");

    @Test
    @DisplayName("Every handler is charged against the policy the frozen table records")
    void handlerBindings_WhenScanned_MatchTheFrozenTable() {
        assertThat(scanBindings()).containsExactlyElementsOf(FROZEN_BINDINGS);
    }

    private static List<String> scanBindings() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.danteplanner.backend");

        return production.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(RateLimitCoverageRule::isRequestHandler)
                .map(RateLimitBindingFreezeTest::binding)
                .sorted()
                .toList();
    }

    private static String binding(JavaMethod method) {
        String handler = method.getOwner().getSimpleName() + "." + method.getName();
        RateLimited declaration = RateLimitCoverageRule.governingDeclaration(method);

        if (declaration == null) {
            return handler + " -> EXEMPT";
        }

        String policy = declaration.value().name();
        String endpoint = declaration.endpoint().isEmpty() ? "" : ":" + declaration.endpoint();
        return handler + " -> " + policy + endpoint + " (" + declaration.denial() + ")";
    }
}
