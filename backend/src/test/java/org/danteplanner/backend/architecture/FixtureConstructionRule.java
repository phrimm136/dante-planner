package org.danteplanner.backend.architecture;

import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.user.entity.User;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class FixtureConstructionRule {

    private static final String SUPPORT = "org.danteplanner.backend.support";

    static final ArchRule FIXTURES_ONLY_IN_SUPPORT =
            classes()
                    .that().resideOutsideOfPackage(SUPPORT)
                    .should(constructNoFixtureByHand())
                    .as("no test outside " + SUPPORT + " calls User.builder(), Planner.created(),"
                            + " PlannerContent.created() or a constructor of User, StoredPlanner, PlannerPublication,"
                            + " PlannerModeration or UpsertPlannerRequest; fixtures come from TestDataFactory,"
                            + " PlannerRows and UpsertRequests");

    private static final Map<String, String> FACTORY_CALLS =
            Map.of(User.class.getName(), "builder", Planner.class.getName(), "created",
                    PlannerContent.class.getName(), "created");
    private static final Set<String> CONSTRUCTED_TYPES = Set.of(User.class.getName(), StoredPlanner.class.getName(),
            PlannerPublication.class.getName(), PlannerModeration.class.getName(),
            UpsertPlannerRequest.class.getName());

    private FixtureConstructionRule() {
    }

    private static ArchCondition<JavaClass> constructNoFixtureByHand() {
        return new ArchCondition<>("construct no fixture by hand") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                clazz.getCodeUnitAccessesFromSelf().stream()
                        .filter(FixtureConstructionRule::constructsAFixture)
                        .forEach(access -> report(access, events));
            }
        };
    }

    private static boolean constructsAFixture(JavaCodeUnitAccess<?> access) {
        String owner = access.getTargetOwner().getName();
        if (access.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)) {
            return CONSTRUCTED_TYPES.contains(owner);
        }
        return access.getName().equals(FACTORY_CALLS.get(owner));
    }

    private static void report(JavaAccess<?> access, ConditionEvents events) {
        events.add(SimpleConditionEvent.violated(access, access.getOrigin().getFullName() + " constructs "
                + access.getTargetOwner().getName() + "; use TestDataFactory or UpsertRequests"));
    }
}
