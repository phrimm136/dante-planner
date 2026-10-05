package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.danteplanner.backend.integration.ExclusiveDatabaseSupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class ExclusiveDatabaseRule {

    static final ArchRule PLANNER_TABLES_EMPTIED_ONLY_BY_EXCLUSIVE_MEMBERS =
            classes()
                    .should(emptyPlannerTablesOnlyFromAnExclusiveMember())
                    .as("only a subclass of ExclusiveDatabaseSupport calls emptyPlannerTables; anywhere else it"
                            + " empties the shared database under its concurrent neighbours");

    private static final String EMPTY_PLANNER_TABLES = "emptyPlannerTables";

    private ExclusiveDatabaseRule() {
    }

    private static ArchCondition<JavaClass> emptyPlannerTablesOnlyFromAnExclusiveMember() {
        return new ArchCondition<>("call emptyPlannerTables only from a subclass of ExclusiveDatabaseSupport") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (clazz.isAssignableTo(ExclusiveDatabaseSupport.class)) {
                    return;
                }
                clazz.getMethodCallsFromSelf().stream()
                        .filter(ExclusiveDatabaseRule::emptiesPlannerTables)
                        .forEach(call -> events.add(SimpleConditionEvent.violated(
                                call,
                                call.getOrigin().getFullName()
                                        + " empties the planner tables outside ExclusiveDatabaseSupport")));
            }
        };
    }

    private static boolean emptiesPlannerTables(JavaMethodCall call) {
        return call.getName().equals(EMPTY_PLANNER_TABLES)
                && call.getTargetOwner().isAssignableTo(ExclusiveDatabaseSupport.class);
    }
}
