package org.danteplanner.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Ratchets the {@code methodName_WhenCondition_ExpectedBehavior} test-method convention
 * (docs/32 phase 11 swept the suite to it; phase 13 locks it).
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
     * {@code @ParameterizedTest}, {@code @RepeatedTest}) must be named with at least two
     * underscore-separated segments, e.g. {@code findById_WhenExists_ReturnsUser}.
     */
    @ArchTest
    static final ArchRule test_methods_follow_naming_convention =
            methods()
                    .that().areMetaAnnotatedWith("org.junit.platform.commons.annotation.Testable")
                    .should().haveNameMatching("^[a-z][A-Za-z0-9]*(_[A-Za-z0-9]+){2,}$")
                    .as("test methods must be named methodName_WhenCondition_ExpectedBehavior");
}
