package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;
import org.danteplanner.backend.shared.ratelimit.RateLimited;

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
            "AuthController.appleCallback -> AUTH (REDIRECT_LOGIN)",
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
            "PlannerCommandController.upsertPlanner -> CRUD:upsert (RESPOND)",
            "PlannerCommentSseController.subscribeToComments -> PLANNER_COMMENT_SSE (RESPOND)",
            "PlannerEngagementController.castUpvote -> CRUD:vote (RESPOND)",
            "PlannerEngagementController.submitReport -> REPORT (RESPOND)",
            "PlannerEngagementController.toggleSubscription -> CRUD:subscribe (RESPOND)",
            "PlannerPublishingController.publishPlanner -> CRUD:publish (RESPOND)",
            "PlannerPublishingController.toggleOwnerNotifications -> CRUD:notifications-toggle (RESPOND)",
            "PlannerPublishingController.unpublishPlanner -> CRUD:unpublish (RESPOND)",
            "PlannerQueryController.getPlanner -> CRUD:get (RESPOND)",
            "PlannerQueryController.getPlannerBatch -> CRUD:batch (RESPOND)",
            "PlannerQueryController.getPlanners -> CRUD:list (RESPOND)",
            "PlannerQueryController.pullPlannerBatch -> CRUD:batch (RESPOND)",
            "PlannerSseController.subscribeToEvents -> SSE (RESPOND)",
            "PublishedPlannerController.getPublishedPlanner -> PUBLIC_READ (RESPOND)",
            "PublishedPlannerController.getPublishedPlannerFlags -> PLANNER_STATS (RESPOND)",
            "PublishedPlannerController.getPublishedPlannerStats -> PLANNER_STATS (RESPOND)",
            "PublishedPlannerController.getPublishedPlanners -> PUBLIC_READ (RESPOND)",
            "PublishedPlannerController.getRecommendedPlanners -> PUBLIC_READ (RESPOND)",
            "PublishedPlannerController.recordView -> VIEW_RECORD (RESPOND)",
            "SseController.subscribe -> SSE (RESPOND)",
            "UserController.deleteMyAccount -> CRUD:user-delete (RESPOND)",
            "UserController.getEpithets -> EXEMPT",
            "UserController.getSettings -> EXEMPT",
            "UserController.updateSettings -> CRUD:user-settings-update (RESPOND)",
            "UserController.updateUsernameEpithet -> CRUD:user-epithet-update (RESPOND)");

    private static final String CONFIG_VERSION_PROPERTY = "rate-limit.config-version";

    private static final List<String> FROZEN_LIMITS = List.of(
            "rate-limit.config-version=1",
            "CRUD -> capacity 60, refill 60 per 60s",
            "SSE -> capacity 15, refill 10 per 10s",
            "COMMENT -> capacity 10, refill 10 per 60s",
            "REPORT -> capacity 3, refill 3 per 3600s",
            "MODERATION -> capacity 20, refill 20 per 60s",
            "AUTH -> capacity 5, refill 5 per 60s",
            "PLANNER_COMMENT_SSE -> capacity 15, refill 10 per 10s",
            "PUBLIC_READ -> capacity 120, refill 120 per 60s",
            "PLANNER_STATS -> capacity 120, refill 120 per 60s",
            "VIEW_RECORD -> capacity 30, refill 30 per 60s");

    @Test
    void handlerBindings_WhenScanned_MatchTheFrozenTable() {
        assertThat(scanBindings()).containsExactlyElementsOf(FROZEN_BINDINGS);
    }

    @Test
    void policyLimits_WhenBoundFromApplicationProperties_MatchTheFrozenTable() throws IOException {
        List<String> bound = boundLimits();

        assertThat(bound)
                .withFailMessage(() -> limitDriftMessage(bound))
                .containsExactlyElementsOf(FROZEN_LIMITS);
    }

    private static String limitDriftMessage(List<String> bound) {
        if (bound.getFirst().equals(FROZEN_LIMITS.getFirst())) {
            return ("Rate limits changed but " + CONFIG_VERSION_PROPERTY + " did not: a live bucket keeps its"
                    + " old limit until the version rises. Bump " + CONFIG_VERSION_PROPERTY
                    + " in application.properties, then re-freeze this table.%nfrozen: %s%nbound:  %s")
                    .formatted(FROZEN_LIMITS, bound);
        }
        return ("Rate-limit configuration differs from the frozen table; re-freeze it with the new"
                + " limits and " + CONFIG_VERSION_PROPERTY + ".%nfrozen: %s%nbound:  %s")
                .formatted(FROZEN_LIMITS, bound);
    }

    private static List<String> boundLimits() throws IOException {
        Properties application = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties"));
        RateLimitProperties properties = new Binder(new MapConfigurationPropertySource(application))
                .bind("rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.properties binds no rate-limit.*"));

        return Stream.concat(
                        Stream.of(CONFIG_VERSION_PROPERTY + "=" + properties.getConfigVersion()),
                        Arrays.stream(RateLimitPolicy.values())
                                .map(policy -> limit(policy, policy.bucket(properties))))
                .toList();
    }

    private static String limit(RateLimitPolicy policy, BucketConfig bucket) {
        return policy.name() + " -> capacity " + bucket.getCapacity()
                + ", refill " + bucket.getRefillTokens() + " per " + bucket.getRefillDurationSeconds() + "s";
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
