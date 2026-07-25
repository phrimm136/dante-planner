package org.danteplanner.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Ratchets the test-method naming contract: a subject, a condition, and an expectation, separated
 * by underscores.
 *
 * <p>Enforced here rather than via Checkstyle {@code MethodName} on purpose: Checkstyle
 * cannot scope a name rule to annotated methods, so the strict regex would also reject the
 * suite's legitimate camelCase helpers ({@code createTestUser}, {@code setUp}, ...) and break
 * the zero-suppression baseline. ArchUnit is annotation-aware, so the rule targets exactly the
 * test methods and leaves helpers untouched. The regex is byte-identical to the phase-11
 * sweep pattern.</p>
 *
 * <p>Config includes test classes ({@code @AnalyzeClasses} default) — a
 * {@code DoNotIncludeTests} import option would make this rule pass vacuously against zero
 * methods.</p>
 */
@AnalyzeClasses(packages = "org.danteplanner.backend")
class TestNamingConventionTest {

    /**
     * Every JUnit test method (anything meta-annotated {@code @Testable}: {@code @Test},
     * {@code @ParameterizedTest}, {@code @RepeatedTest}, {@code @TestFactory}) carries three or
     * more underscore-separated parts: subject, condition, expectation.
     *
     * <p>Segment casing is deliberately free, because two spellings are both legitimate and both
     * in wide use. The mechanical form covers most of the suite, in either casing —
     * {@code findById_WhenExists_ReturnsUser}, {@code unsafeMethod_missingHeader_rejected}. The
     * all-lowercase invariant phrase covers tests a comment cites by name, which must survive the
     * rename of whatever they cover — {@code deleted_planner_is_masked_on_replica_hit}; see
     * {@code docs/testing-principles.md} §7.</p>
     *
     * <p>Do not tighten this to require a literal {@code When} or PascalCase segments. Fewer than
     * half the suite spells it that way, and the invariant form uses English articles
     * ({@code a_}, {@code an_}, {@code no_}) that a minimum-segment-length rule would reject.</p>
     */
    @ArchTest
    static final ArchRule test_methods_follow_naming_convention =
            methods()
                    .that().areMetaAnnotatedWith("org.junit.platform.commons.annotation.Testable")
                    .should().haveNameMatching("^[a-z][A-Za-z0-9]*(_[A-Za-z0-9]+){2,}$")
                    .as("test methods carry three or more underscore-separated parts:"
                            + " subject, condition, expectation");
}
