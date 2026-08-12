package org.danteplanner.backend.architecture;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the cross-feature dependency graph after the package-by-feature move (B12).
 *
 * <p>Each feature package ({@code admin, auth, comment, moderation, notification, planner, shared,
 * user}) may only reach the other features on its allowlist. The rules below are the negative form
 * of that allowlist: they name the features each package must NOT depend on. Any new cross-feature
 * edge that is not already part of the frozen graph makes the owning feature's rule fail, forcing
 * the edge to be declared here deliberately rather than accreting silently.</p>
 *
 * <p>The universal edges {@code * -> shared} and {@code * -> auth} (AuthenticationService, token
 * infrastructure) are permitted everywhere and therefore never appear in any disallowed list.</p>
 *
 * <p>{@code shared} is intended as a dependency sink. It is not a <em>pure</em> sink today:
 * {@code GlobalExceptionHandler} references every feature's exceptions, the security filter reaches
 * {@code user}/{@code auth.token}, and a few config classes read {@code user}/{@code planner} types
 * — those out-edges are inherent while those classes live in {@code shared}. The
 * {@link #shared_should_not_depend_on_admin_or_notification} rule freezes the sink in its true,
 * currently-passing form: {@code shared} must not grow edges into {@code admin} or
 * {@code notification}.</p>
 *
 * <p>The read seam {@code shared.readpath} is held to a stricter, feature-agnostic contract by
 * {@link #shared_readpath_must_not_depend_on_planner}: it dereferences entities through opaque
 * suppliers and a base {@code EntityNotFoundException}, so it must carry no dependency on
 * {@code planner} (or any feature). The broader {@code shared}-wide planner edges that remain
 * ({@code GlobalExceptionHandler} handling planner exceptions, a config class reading a planner
 * entity) are inherent to those classes living in {@code shared} and are out of this rule's scope.</p>
 *
 * <p>The rules above constrain which features may see each other at all. The internals rules below
 * constrain how: a feature's {@code ..repository..}, {@code ..validation..} and {@code ..entity..}
 * packages are private to that feature, so a permitted cross-feature edge must land on the owning
 * feature's service and DTOs, never on its persistence, its invariants, or its mapped rows. Without
 * them a feature package can reach straight into another's internals while every feature-granular
 * rule still passes.</p>
 *
 * <p>The edges that already exist are frozen by name in the {@code *_INTERNALS_FROZEN} lists.
 * {@link #frozenInternalEdges_WhenScanned_AreStillLive} fails once an entry stops describing a real
 * edge. An entry names a source file, so a nested class is covered by its outer one.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureBoundaryTest {

    private static final String MAIN = "org.danteplanner.backend.";

    private static final Set<String> COMMENT_INTERNALS_FROZEN = Set.of(
            MAIN + "moderation.service.CommentModerationService",
            MAIN + "moderation.service.CommentReportService");

    private static final Set<String> MODERATION_INTERNALS_FROZEN = Set.of(
            MAIN + "admin.service.AdminService");

    private static final Set<String> PLANNER_INTERNALS_FROZEN = Set.of(
            MAIN + "comment.service.CommentCommandService",
            MAIN + "comment.service.CommentEngagementService",
            MAIN + "comment.validation.CommentAccessValidator",
            MAIN + "moderation.controller.ModerationController",
            MAIN + "moderation.dto.ModerationResponse",
            MAIN + "moderation.service.PlannerModerationService",
            MAIN + "shared.config.WebConfig",
            MAIN + "shared.exception.GlobalExceptionHandler");

    /**
     * The widest of the lists: {@code user.entity} carries the account types the whole tree reads —
     * {@code User} on a planner, {@code UserRole} in a token claim, {@code RestrictionState} in a
     * moderation decision.
     */
    private static final Set<String> USER_INTERNALS_FROZEN = Set.of(
            MAIN + "admin.dto.ChangeRoleRequest",
            MAIN + "admin.dto.UserRoleResponse",
            MAIN + "admin.service.AdminService",
            MAIN + "auth.service.AuthenticationService",
            MAIN + "auth.token.JwtTokenService",
            MAIN + "auth.token.TokenClaims",
            MAIN + "auth.token.TokenGenerator",
            MAIN + "comment.dto.CommentTreeNode",
            MAIN + "comment.service.CommentCommandService",
            MAIN + "comment.service.CommentQueryService",
            MAIN + "moderation.controller.ModerationController",
            MAIN + "moderation.dto.ModeratedUserResponse",
            MAIN + "moderation.dto.TimeoutResponse",
            MAIN + "moderation.service.ModerationPolicy",
            MAIN + "moderation.service.ModerationQueryService",
            MAIN + "moderation.service.UserModerationService",
            MAIN + "planner.dto.PlannerPublishedPayload",
            MAIN + "planner.dto.PublishedPlannerDetailResponse",
            MAIN + "planner.entity.Planner",
            MAIN + "planner.service.PlannerAccessGuard",
            MAIN + "planner.service.PlannerCommandService",
            MAIN + "planner.service.PlannerEngagementService",
            MAIN + "shared.security.AccessTokenAuthenticator",
            MAIN + "shared.security.JwtAuthenticationFilter",
            MAIN + "shared.sse.SseService");

    private static final Map<String, Set<String>> FROZEN_INTERNAL_EDGES = Map.of(
            "comment", COMMENT_INTERNALS_FROZEN,
            "moderation", MODERATION_INTERNALS_FROZEN,
            "notification", Set.of(),
            "planner", PLANNER_INTERNALS_FROZEN,
            "user", USER_INTERNALS_FROZEN);

    @ArchTest
    static final ArchRule admin_only_depends_on_auth_user_shared =
            noClasses()
                    .that().resideInAPackage("..admin..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..comment..", "..notification..", "..planner..")
                    .as("admin may only depend on auth, user, shared, moderation (+ its own package)");
    // moderation is permitted for one reason: a role change is a moderator action and must leave an
    // audit record in the same transaction, which rules out an after-commit listener as the seam.

    @ArchTest
    static final ArchRule auth_only_depends_on_user_shared =
            noClasses()
                    .that().resideInAPackage("..auth..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..admin..", "..comment..", "..moderation..", "..notification..", "..planner..")
                    .as("auth may only depend on user, shared (+ its own package)");

    @ArchTest
    static final ArchRule comment_does_not_depend_on_admin =
            noClasses()
                    .that().resideInAPackage("..comment..")
                    .should().dependOnClassesThat().resideInAPackage("..admin..")
                    .as("comment may depend on auth, moderation, notification, planner, shared, user — not admin");

    @ArchTest
    static final ArchRule moderation_does_not_depend_on_admin_or_notification =
            noClasses()
                    .that().resideInAPackage("..moderation..")
                    .should().dependOnClassesThat().resideInAnyPackage("..admin..", "..notification..")
                    .as("moderation may depend on auth, comment, planner, shared, user — not admin or notification");

    @ArchTest
    static final ArchRule notification_does_not_depend_on_admin_comment_moderation =
            noClasses()
                    .that().resideInAPackage("..notification..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..admin..", "..comment..", "..moderation..")
                    .as("notification may depend on auth, planner, shared, user — not admin, comment, moderation");

    @ArchTest
    static final ArchRule planner_does_not_depend_on_admin =
            noClasses()
                    .that().resideInAPackage("..planner..")
                    .should().dependOnClassesThat().resideInAPackage("..admin..")
                    .as("planner may depend on auth, comment, moderation, notification, shared, user — not admin");

    @ArchTest
    static final ArchRule shared_should_not_depend_on_admin_or_notification =
            noClasses()
                    .that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage("..admin..", "..notification..")
                    .as("shared is a dependency sink: it must not grow edges into admin or notification");

    @ArchTest
    static final ArchRule shared_readpath_must_not_depend_on_planner =
            noClasses()
                    .that().resideInAPackage("..shared.readpath..")
                    .should().dependOnClassesThat().resideInAPackage("..planner..")
                    .as("the shared read seam must stay feature-agnostic: shared.readpath must not depend on planner");

    @ArchTest
    static final ArchRule user_does_not_depend_on_admin_or_notification =
            noClasses()
                    .that().resideInAPackage("..user..")
                    .should().dependOnClassesThat().resideInAnyPackage("..admin..", "..notification..")
                    .as("user may depend on auth, comment, moderation, planner, shared — not admin or notification");

    @ArchTest
    static final ArchRule comment_internals_are_private_to_comment =
            noClasses()
                    .that().resideOutsideOfPackage("..comment..").and(notFrozenFor("comment"))
                    .should().dependOnClassesThat().resideInAnyPackage(internalsOf("comment"))
                    .as("comment internals are private to comment: outside callers go through a comment service");

    @ArchTest
    static final ArchRule moderation_internals_are_private_to_moderation =
            noClasses()
                    .that().resideOutsideOfPackage("..moderation..").and(notFrozenFor("moderation"))
                    .should().dependOnClassesThat().resideInAnyPackage(internalsOf("moderation"))
                    .as("moderation internals are private to moderation: outside callers go through a moderation service");

    @ArchTest
    static final ArchRule notification_internals_are_private_to_notification =
            noClasses()
                    .that().resideOutsideOfPackage("..notification..").and(notFrozenFor("notification"))
                    .should().dependOnClassesThat().resideInAnyPackage(internalsOf("notification"))
                    .as("notification internals are private to notification: outside callers go through a notification service");

    @ArchTest
    static final ArchRule planner_internals_are_private_to_planner =
            noClasses()
                    .that().resideOutsideOfPackage("..planner..").and(notFrozenFor("planner"))
                    .should().dependOnClassesThat().resideInAnyPackage(internalsOf("planner"))
                    .as("planner internals are private to planner: outside callers go through a planner service");

    @ArchTest
    static final ArchRule user_internals_are_private_to_user =
            noClasses()
                    .that().resideOutsideOfPackage("..user..").and(notFrozenFor("user"))
                    .should().dependOnClassesThat().resideInAnyPackage(internalsOf("user"))
                    .as("user internals are private to user: outside callers go through a user service");

    @ArchTest
    static void frozenInternalEdges_WhenScanned_AreStillLive(JavaClasses classes) {
        Set<String> stale = new TreeSet<>();
        FROZEN_INTERNAL_EDGES.forEach((feature, frozen) -> frozen.stream()
                .filter(origin -> !reachesInternalsOf(classes, origin, feature))
                .forEach(stale::add));

        assertThat(stale)
                .as("these no longer reach the feature's internals, so the entry now excuses an "
                        + "edge nobody has; delete it and let the rule cover the class again")
                .isEmpty();
    }

    private static String[] internalsOf(String feature) {
        return new String[] {
                "..%s.repository..".formatted(feature),
                "..%s.validation..".formatted(feature),
                "..%s.entity..".formatted(feature),
        };
    }

    private static DescribedPredicate<JavaClass> notFrozenFor(String feature) {
        Set<String> frozen = FROZEN_INTERNAL_EDGES.get(feature);
        return DescribedPredicate.describe(
                "are not frozen against " + feature,
                javaClass -> !frozen.contains(topLevel(javaClass)));
    }

    private static boolean reachesInternalsOf(JavaClasses classes, String origin, String feature) {
        String root = MAIN + feature + ".";
        return classes.stream()
                .filter(javaClass -> topLevel(javaClass).equals(origin))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getFullName())
                .anyMatch(target -> target.startsWith(root + "repository.")
                        || target.startsWith(root + "validation.")
                        || target.startsWith(root + "entity."));
    }

    /**
     * An entry names a source file, so a nested or synthesized class — a Lombok builder, an enum
     * switch map — is frozen with the class it was generated from rather than by a name the
     * compiler chose.
     */
    private static String topLevel(JavaClass javaClass) {
        String fullName = javaClass.getFullName();
        int nested = fullName.indexOf('$');
        return nested < 0 ? fullName : fullName.substring(0, nested);
    }
}
