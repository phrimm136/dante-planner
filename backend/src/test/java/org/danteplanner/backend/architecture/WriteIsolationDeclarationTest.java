package org.danteplanner.backend.architecture;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;

import org.danteplanner.backend.planner.service.PlannerCatalogService;

import static org.assertj.core.api.Assertions.assertThat;

class WriteIsolationDeclarationTest {

    private static final JavaClasses PRODUCTION = ImportedClasses.production();

    private static final String ACCOUNT_DELETION =
            "org.danteplanner.backend.user.service.UserAccountLifecycleService.deleteAccount(java.lang.Long)";

    private static final String OWNER_UNPUBLISH = "org.danteplanner.backend.planner.service.PlannerPublishingService"
            + ".unpublish(java.lang.Long, java.util.UUID)";

    private static final String MODERATOR_TAKEDOWN = "org.danteplanner.backend.moderation.service.PlannerModerationService"
            + ".deletePlanner(java.lang.Long, java.util.UUID, java.lang.String)";

    private static final String MODERATOR_UNPUBLISH = "org.danteplanner.backend.moderation.service.PlannerModerationService"
            + ".unpublishPlanner(java.lang.Long, java.util.UUID)";

    private static final String ACCOUNT_PURGE = "org.danteplanner.backend.user.service.UserAccountLifecycleService"
            + ".performHardDelete(java.lang.Long, java.time.Instant)";

    private static final Set<String> BULK_PROJECTION_DELETES = Set.of("deleteAllByPlannerIds", "deleteViewsByPlannerIds");

    @Test
    void filterRowWrites_WhenWalkedUpEveryCallerPath_RunAtReadCommittedOrAccountDeletionsSerializable() {
        Walk walk = walkFrom(filterRowWrites(PRODUCTION));

        assertThat(walk.transactional())
                .as("the walk reaches the owner and moderator writes, so it cannot pass by finding nothing")
                .containsKeys(OWNER_UNPUBLISH, ACCOUNT_DELETION, MODERATOR_TAKEDOWN, MODERATOR_UNPUBLISH);
        assertThat(walk.transactional()).allSatisfy((name, isolation) -> assertThat(isolation)
                .as("%s opens or joins a transaction that rebuilds or clears filter rows", name)
                .isEqualTo(name.equals(ACCOUNT_DELETION) ? Isolation.SERIALIZABLE : Isolation.READ_COMMITTED));
        assertThat(walk.withoutTransaction())
                .as("these reach a filter-row write with no transaction above them")
                .isEmpty();
    }

    @Test
    void bulkProjectionDeletes_WhenWalkedUpEveryCallerPath_RunAtReadCommitted() {
        Walk walk = walkFrom(bulkProjectionDeletes(PRODUCTION));

        assertThat(walk.transactional())
                .as("the walk reaches the account purge, so it cannot pass by finding nothing")
                .containsKey(ACCOUNT_PURGE);
        assertThat(walk.transactional()).allSatisfy((name, isolation) -> assertThat(isolation)
                .as("%s opens or joins a transaction that bulk-deletes planner projections", name)
                .isEqualTo(Isolation.READ_COMMITTED));
        assertThat(walk.withoutTransaction())
                .as("these reach a projection bulk delete with no transaction above them")
                .isEmpty();
    }

    @Test
    void catalogFilterWrites_WhenDeclared_JoinTheCallersTransaction() {
        assertThat(filterRowWrites(PRODUCTION)).extracting(JavaCodeUnit::getName)
                .containsExactlyInAnyOrder("syncFilterRows", "onBecameVisible", "onBecameInvisible",
                        "accountDeactivated", "accountReactivated", "onVisibleEditCommitted");
    }

    private record Walk(Map<String, Isolation> transactional, Set<String> withoutTransaction) {
    }

    private record Step(JavaCodeUnit unit, boolean covered) {
    }

    private static Set<JavaMethod> filterRowWrites(JavaClasses classes) {
        Set<JavaMethod> writes = new HashSet<>();
        classes.get(PlannerCatalogService.class).getMethods().stream()
                .filter(method -> transactional(method)
                        .filter(transactional -> transactional.propagation() == Propagation.MANDATORY)
                        .isPresent())
                .forEach(writes::add);
        return writes;
    }

    private static Set<JavaMethod> bulkProjectionDeletes(JavaClasses classes) {
        Set<JavaMethod> deletes = new HashSet<>();
        classes.stream()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> BULK_PROJECTION_DELETES.contains(method.getName()))
                .forEach(deletes::add);
        return deletes;
    }

    private static Walk walkFrom(Collection<? extends JavaCodeUnit> roots) {
        Map<String, Isolation> transactional = new TreeMap<>();
        Set<String> withoutTransaction = new TreeSet<>();
        Set<String> visited = new HashSet<>();
        Deque<Step> pending = new ArrayDeque<>();
        roots.forEach(root -> pending.push(new Step(root, false)));
        while (!pending.isEmpty()) {
            Step step = pending.pop();
            if (!visited.add(step.unit().getFullName() + "/" + step.covered())) {
                continue;
            }
            Set<JavaCodeUnit> callers = callersOf(step.unit());
            if (callers.isEmpty() && !step.covered()) {
                withoutTransaction.add(step.unit().getFullName());
            }
            for (JavaCodeUnit caller : callers) {
                Optional<Transactional> declared = transactional(caller)
                        .filter(annotation -> annotation.propagation() != Propagation.MANDATORY);
                declared.ifPresent(annotation -> transactional.put(caller.getFullName(), annotation.isolation()));
                pending.push(new Step(caller, step.covered() || declared.isPresent()));
            }
        }
        return new Walk(transactional, withoutTransaction);
    }

    private static Set<JavaCodeUnit> callersOf(JavaCodeUnit unit) {
        Set<JavaCodeUnit> callers = new HashSet<>();
        unit.getAccessesToSelf().forEach(access -> callers.add(access.getOrigin()));
        if (unit instanceof JavaMethod method) {
            String[] parameters = method.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);
            Stream.concat(method.getOwner().getAllRawInterfaces().stream(),
                            method.getOwner().getAllRawSuperclasses().stream())
                    .flatMap(type -> type.tryGetMethod(method.getName(), parameters).stream())
                    .forEach(inherited -> inherited.getAccessesToSelf()
                            .forEach(access -> callers.add(access.getOrigin())));
        }
        return callers;
    }

    private static Optional<Transactional> transactional(JavaCodeUnit unit) {
        return unit.tryGetAnnotationOfType(Transactional.class)
                .or(() -> unit.getOwner().tryGetAnnotationOfType(Transactional.class));
    }
}
