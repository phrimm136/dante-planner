package org.danteplanner.backend.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class JunitAssertionsRule {

    private static final String JUNIT_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String HAMCREST_ASSERT = "org.hamcrest.MatcherAssert";

    static final ArchRule ASSERTJ_ONLY =
            noClasses()
                    .should().dependOnClassesThat().haveFullyQualifiedName(JUNIT_ASSERTIONS)
                    .orShould().dependOnClassesThat().haveFullyQualifiedName(HAMCREST_ASSERT)
                    .as("no test asserts through " + JUNIT_ASSERTIONS + " or " + HAMCREST_ASSERT
                            + "; assertions are AssertJ");

    private JunitAssertionsRule() {
    }
}
