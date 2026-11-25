package org.danteplanner.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.failure.Result;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultRuleTest {

    private static final String CHECK_RETURN_VALUE = "com.google.errorprone.annotations.CheckReturnValue";

    private static final String FIXTURE_PACKAGE = "org.danteplanner.fixture.failureunion";

    static final ArchRule RESULTS_ARE_READ =
            methods()
                    .that(returnAResult())
                    .should(carryCheckReturnValue())
                    .as("a method returning Result carries @CheckReturnValue on itself or on a class around it,"
                            + " so a caller that discards the result does not compile");

    @Test
    void production_WhenScanned_AnnotatesEveryMethodReturningAResult() {
        assertThatCode(() -> RESULTS_ARE_READ.check(ImportedClasses.production())).doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenNeitherTheMethodNorAClassAroundItIsAnnotated_FailsNamingOnlyThatMethod() {
        assertThatThrownBy(() -> RESULTS_ARE_READ.check(new ClassFileImporter().importPackages(FIXTURE_PACKAGE)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(FIXTURE_PACKAGE + ".ResultReturners$Unannotated.discardable()")
                .hasMessageContaining("(1 times)");
    }

    private static DescribedPredicate<JavaMethod> returnAResult() {
        return describe("return a Result", method -> method.getRawReturnType().isEquivalentTo(Result.class)
                && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                && !method.getOwner().isEquivalentTo(Result.class));
    }

    private static ArchCondition<JavaMethod> carryCheckReturnValue() {
        return new ArchCondition<>("carry @CheckReturnValue") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!method.isAnnotatedWith(CHECK_RETURN_VALUE) && !isAnnotatedAround(method.getOwner())) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns a Result its caller may discard"));
                }
            }
        };
    }

    private static boolean isAnnotatedAround(JavaClass owner) {
        return owner.isAnnotatedWith(CHECK_RETURN_VALUE)
                || owner.getEnclosingClass().filter(ResultRuleTest::isAnnotatedAround).isPresent();
    }
}
