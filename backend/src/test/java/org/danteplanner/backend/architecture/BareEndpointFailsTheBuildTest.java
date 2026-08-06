package org.danteplanner.backend.architecture;

import org.danteplanner.ratelimitfixture.BareHandlerFixture;
import org.danteplanner.ratelimitfixture.DeclaredHandlerFixture;
import org.danteplanner.ratelimitfixture.MisdeclaredHandlerFixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenario: Bare endpoint fails the build.
 *
 * <p>{@link RateLimitCoverageTest} runs the same rule over the real codebase, where it passes. That
 * proves the codebase is covered, not that the rule can reject anything — so the rule is evaluated
 * here against a handler that omits the declaration, and the failure is read for the handler's
 * name. A message that named only the rule would leave the reader hunting for the offender.</p>
 */
class BareEndpointFailsTheBuildTest {

    @Test
    @DisplayName("Bare endpoint fails the build, and the failure names the handler method")
    void bareHandler_WhenCoverageRuleRuns_FailsNamingTheHandlerMethod() {
        JavaClasses bare = new ClassFileImporter().importClasses(BareHandlerFixture.class);

        assertThatThrownBy(() -> RateLimitCoverageRule.REQUEST_HANDLERS_DECLARE_A_RATE_LIMIT_POLICY.check(bare))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(BareHandlerFixture.class.getName() + ".bare()")
                        .contains("@RateLimited"));
    }

    @Test
    @DisplayName("A class-level policy and a method-level exemption both satisfy coverage")
    void declaredHandlers_WhenCoverageRuleRuns_Pass() {
        JavaClasses declared = new ClassFileImporter().importClasses(DeclaredHandlerFixture.class);

        assertThatCode(() -> RateLimitCoverageRule.REQUEST_HANDLERS_DECLARE_A_RATE_LIMIT_POLICY.check(declared))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A per-route policy declared without an endpoint fails the build")
    void perRoutePolicy_WhenDeclaredWithoutEndpoint_FailsTheShapeRule() {
        JavaClasses misdeclared = new ClassFileImporter().importClasses(MisdeclaredHandlerFixture.class);

        assertThatThrownBy(() -> RateLimitCoverageRule.DECLARATIONS_MATCH_THEIR_POLICY_SHAPE.check(misdeclared))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(MisdeclaredHandlerFixture.class.getName() + ".crudWithoutEndpoint()")
                        .contains("names no endpoint"));
    }

    @Test
    @DisplayName("A self-naming policy given an endpoint anyway fails the build")
    void selfNamingPolicy_WhenGivenAnEndpoint_FailsTheShapeRule() {
        JavaClasses misdeclared = new ClassFileImporter().importClasses(MisdeclaredHandlerFixture.class);

        assertThatThrownBy(() -> RateLimitCoverageRule.DECLARATIONS_MATCH_THEIR_POLICY_SHAPE.check(misdeclared))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(
                                MisdeclaredHandlerFixture.class.getName()
                                        + ".selfEndpointPolicyNamingAnEndpoint()")
                        .contains("names its own endpoint"));
    }

    @Test
    @DisplayName("Correctly shaped declarations satisfy the shape rule")
    void declaredHandlers_WhenShapeRuleRuns_Pass() {
        JavaClasses declared = new ClassFileImporter().importClasses(DeclaredHandlerFixture.class);

        assertThatCode(() -> RateLimitCoverageRule.DECLARATIONS_MATCH_THEIR_POLICY_SHAPE.check(declared))
                .doesNotThrowAnyException();
    }
}
