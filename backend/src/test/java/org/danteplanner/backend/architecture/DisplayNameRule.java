package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.DisplayName;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class DisplayNameRule {

    static final ArchRule TESTS_CARRY_NO_DISPLAY_NAME =
            classes()
                    .should(carryNoDisplayNameOnTheClassOrItsMethods())
                    .as("no test class or test method carries a DisplayName annotation;"
                            + " the method name is the only name");

    private DisplayNameRule() {
    }

    private static ArchCondition<JavaClass> carryNoDisplayNameOnTheClassOrItsMethods() {
        return new ArchCondition<>("carry no DisplayName annotation on the class or its methods") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (clazz.isAnnotatedWith(DisplayName.class)) {
                    events.add(SimpleConditionEvent.violated(
                            clazz, clazz.getName() + " carries a DisplayName annotation"));
                }
                clazz.getMethods().stream()
                        .filter(method -> method.isAnnotatedWith(DisplayName.class))
                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                method, method.getFullName() + " carries a DisplayName annotation")));
            }
        };
    }
}
