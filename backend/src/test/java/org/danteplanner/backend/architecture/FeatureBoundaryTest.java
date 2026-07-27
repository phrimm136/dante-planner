package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
 * <p>The rules above constrain which features may see each other at all. The repository rules below
 * constrain how: a feature's {@code ..repository..} package is private to that feature, so a
 * permitted cross-feature edge must land on the owning feature's service, never on its repository.
 * Without them a feature package can reach straight into another's persistence layer while every
 * feature-granular rule still passes.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureBoundaryTest {

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
    static final ArchRule comment_repositories_are_private_to_comment =
            noClasses()
                    .that().resideOutsideOfPackage("..comment..")
                    .should().dependOnClassesThat().resideInAPackage("..comment.repository..")
                    .as("comment.repository is private to comment: outside callers go through a comment service");

    @ArchTest
    static final ArchRule moderation_repositories_are_private_to_moderation =
            noClasses()
                    .that().resideOutsideOfPackage("..moderation..")
                    .should().dependOnClassesThat().resideInAPackage("..moderation.repository..")
                    .as("moderation.repository is private to moderation: outside callers go through a moderation service");

    @ArchTest
    static final ArchRule notification_repositories_are_private_to_notification =
            noClasses()
                    .that().resideOutsideOfPackage("..notification..")
                    .should().dependOnClassesThat().resideInAPackage("..notification.repository..")
                    .as("notification.repository is private to notification: outside callers go through a notification service");

    @ArchTest
    static final ArchRule planner_repositories_are_private_to_planner =
            noClasses()
                    .that().resideOutsideOfPackage("..planner..")
                    .should().dependOnClassesThat().resideInAPackage("..planner.repository..")
                    .as("planner.repository is private to planner: outside callers go through a planner service");

    @ArchTest
    static final ArchRule user_repositories_are_private_to_user =
            noClasses()
                    .that().resideOutsideOfPackage("..user..")
                    .should().dependOnClassesThat().resideInAPackage("..user.repository..")
                    .as("user.repository is private to user: outside callers go through a user service");
}
