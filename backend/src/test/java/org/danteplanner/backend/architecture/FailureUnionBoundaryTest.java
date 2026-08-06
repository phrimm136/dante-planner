package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the boundary a failure union may not cross.
 *
 * <p>The transaction proxy keys rollback off an unchecked throw. A failure returned as a value
 * therefore commits every write the method made before it decided to fail, and a caller that reads
 * the value and stops has already lost the undo. Unions stay on the inside: a transactional method
 * may produce and consume one through helpers, and a facade owning no transaction may return one,
 * but no {@code @Transactional} method declares one as its return type.</p>
 *
 * <p>The rule body is shared with fixtures that violate it on purpose, one annotated on the method
 * and one on the class. A rule only ever asserted against a passing codebase demonstrates that the
 * codebase passes, not that the rule rejects anything, and the two cases fail for opposite reasons:
 * a broken rule that matches nothing leaves the first test green and the second red.</p>
 *
 * <p>Known bound: the rule reads the declared raw return type, so a union wrapped in an
 * {@code Optional} or a collection passes unremarked. That is the convention as worded — the union
 * itself is what may not be declared across the proxy.</p>
 */
class FailureUnionBoundaryTest {

    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    private static final String FAILURE_UNION = "org.danteplanner.backend.shared.failure.FailureUnion";

    private static final String FIXTURE_PACKAGE = "org.danteplanner.backend.architecture.fixture";

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter()
            .importPackages(FIXTURE_PACKAGE);

    @Test
    @DisplayName("no transactional method returns a failure union")
    void transactionalMethod_WhenItReturnsAFailureUnion_IsRejected() {
        assertThat(offendersIn(MAIN_CLASSES))
                .as("a failure handed back across the transaction proxy commits the writes made "
                        + "before the decision to fail; throw so the proxy rolls back, or keep the "
                        + "union behind a helper this method consumes rather than returns")
                .isEmpty();
    }

    @Test
    @DisplayName("the rule names the offending method when one exists, annotated either way")
    void offendingMethod_WhenTheRuleRunsOverTheFixture_IsNamed() {
        assertThat(offendersIn(FIXTURE_CLASSES))
                .as("the rule above passes vacuously unless this one shows it rejecting a violation, "
                        + "and no class in main sources is annotated at class level to exercise that "
                        + "half of the condition")
                .containsExactly(
                        FIXTURE_PACKAGE + ".ClassTransactionalUnionReturner.settle",
                        FIXTURE_PACKAGE + ".TransactionalUnionReturner.settle");
    }

    /**
     * Every transactional method in the given classes whose declared return type carries the
     * failure marker.
     *
     * @param classes the classes to scan
     * @return the offenders as {@code owner.method}, sorted, empty when there are none
     */
    private static Set<String> offendersIn(JavaClasses classes) {
        Set<String> offenders = new TreeSet<>();
        for (JavaClass clazz : classes) {
            for (JavaMethod method : clazz.getMethods()) {
                if (isTransactional(method) && isFailureUnion(method.getRawReturnType())) {
                    offenders.add(clazz.getFullName() + "." + method.getName());
                }
            }
        }
        return offenders;
    }

    /** True for a method the proxy wraps, whether it or its class carries the annotation. */
    private static boolean isTransactional(JavaMethod method) {
        return method.isAnnotatedWith(TRANSACTIONAL) || method.getOwner().isAnnotatedWith(TRANSACTIONAL);
    }

    /** True for the marker itself and for every type that inherits it, at any depth. */
    private static boolean isFailureUnion(JavaClass type) {
        return FAILURE_UNION.equals(type.getFullName())
                || type.getAllRawInterfaces().stream()
                        .anyMatch(inherited -> FAILURE_UNION.equals(inherited.getFullName()));
    }
}
