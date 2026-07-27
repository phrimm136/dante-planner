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
     * more underscore-separated parts, spelled {@code subject_WhenCondition_Expectation}: a
     * camelCase subject, a middle part opening with {@code When}, and PascalCase expectation parts
     * — {@code findById_WhenExists_ReturnsUser}.
     *
     * <p>The condition word is fixed rather than drawn from a set. {@code After}, {@code With} and
     * {@code Given} each read well in isolation, and admitting them is what let half the suite
     * settle into spellings that were neither mechanical nor invariant; a reader scanning a file
     * should not have to hold four shapes in mind at once.</p>
     *
     * <p>A name whose middle part reads as an outcome rather than a condition —
     * {@code cannotCreateComment}, {@code NoDeadlock} — is misnamed rather than exempt. The
     * condition belongs in the middle and the outcome at the end, and rewriting it that way
     * usually exposes that the two had been swapped.</p>
     */
    @ArchTest
    static final ArchRule test_methods_follow_naming_convention =
            methods()
                    .that().areMetaAnnotatedWith("org.junit.platform.commons.annotation.Testable")
                    .should().haveNameMatching(
                            "^[a-z][A-Za-z0-9]*_When[A-Z0-9][A-Za-z0-9]*(_[A-Z0-9][A-Za-z0-9]*)+$")
                    .as("test methods are spelled subject_WhenCondition_Expectation");
}
