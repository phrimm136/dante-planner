package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Executable form of the prose conventions in {@code backend/CLAUDE.md}.
 *
 * <p>Every rule here holds against the tree as it stands, so the file is a freeze rather than a
 * target: a violation means new code drifted, never that old code is pending migration. Conventions
 * that the current tree violates are deliberately absent and land with the change that makes them
 * green.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ConventionBaselineTest {

    private static final String ASYNC = "org.springframework.scheduling.annotation.Async";
    private static final String ENABLE_ASYNC = "org.springframework.scheduling.annotation.EnableAsync";
    private static final String TASK_EXECUTOR =
            "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor";
    private static final String REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";
    private static final String VALID = "jakarta.validation.Valid";
    private static final String ENTITY = "jakarta.persistence.Entity";
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /**
     * The async model is {@code @Scheduled} plus {@code @TransactionalEventListener(AFTER_COMMIT)}
     * plus Redis pub/sub. A thread pool introduced alongside it would carry no transaction and no
     * security context, and would run work the pod's shutdown never drains.
     */
    @ArchTest
    static final ArchRule no_async_annotation =
            noMethods()
                    .should().beAnnotatedWith(ASYNC)
                    .as("no @Async: the async model is @Scheduled + AFTER_COMMIT listeners + Redis pub/sub");

    @ArchTest
    static final ArchRule no_async_enablement_or_thread_pools =
            noClasses()
                    .should().beAnnotatedWith(ENABLE_ASYNC)
                    .orShould().dependOnClassesThat().haveFullyQualifiedName(TASK_EXECUTOR)
                    .as("no @EnableAsync and no ThreadPoolTaskExecutor");

    /**
     * Structured logging is the only observable channel: MDC carries the request context that a
     * standard-stream write would drop, and the Gradle test task does not capture application
     * stdout at all.
     */
    @ArchTest
    static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    /**
     * Transaction boundaries belong to the service layer. A boundary opened in a controller spans
     * response serialization; one opened in a repository nests inside the caller's.
     */
    @ArchTest
    static final ArchRule transactions_are_declared_in_the_service_layer =
            classes()
                    .that().areAnnotatedWith(TRANSACTIONAL)
                    .or().containAnyMethodsThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "are annotated with @Transactional",
                                    method -> method.isAnnotatedWith(TRANSACTIONAL)))
                    .should().resideInAPackage("..service..")
                    .as("@Transactional is declared only in the service layer");

    /**
     * Spring's proxy cannot intercept a private method, so {@code @Transactional} on one is silently
     * inert. Stated as "not private" rather than "public" because
     * {@code PlannerCommandService.createPlanner} is deliberately package-private for testability.
     */
    @ArchTest
    static final ArchRule transactional_methods_are_proxyable =
            methods()
                    .that().areAnnotatedWith(TRANSACTIONAL)
                    .should().notBePrivate()
                    .as("@Transactional on a private method is never intercepted by the proxy");

    /**
     * Jakarta validation runs on a request body only where {@code @Valid} sits beside
     * {@code @RequestBody}. Without it the constraints on the DTO are inert and every field arrives
     * unchecked, which no test of a valid payload can reveal.
     */
    @ArchTest
    static final ArchRule request_bodies_are_validated =
            methods()
                    .should(new ArchCondition<JavaMethod>("carry @Valid beside every @RequestBody") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            for (JavaParameter parameter : method.getParameters()) {
                                if (!isAnnotated(parameter, REQUEST_BODY)
                                        || isAnnotated(parameter, VALID)) {
                                    continue;
                                }
                                events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName()
                                                + " takes a @RequestBody without @Valid, so its"
                                                + " constraints never run"));
                            }
                        }
                    })
                    .as("@Valid beside every @RequestBody");

    /**
     * A mapped entity returned from a controller serializes whatever its mapping happens to expose,
     * so a new column becomes a response field nobody chose. Identified by {@code @Entity} rather
     * than by package: {@code shared.entity} holds enums that are legitimate response values.
     * Checked on the type arguments too, because the raw return type is always
     * {@code ResponseEntity}.
     */
    @ArchTest
    static final ArchRule controllers_return_dtos =
            methods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .should(new ArchCondition<JavaMethod>("return a DTO rather than a mapped entity") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            returnedTypes(method).stream()
                                    .filter(type -> type.isAnnotatedWith(ENTITY))
                                    .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                            method.getFullName() + " returns " + type.getName()
                                                    + "; map it to a DTO at the boundary")));
                        }
                    })
                    .as("controllers return DTOs, never mapped entities");

    private static boolean isAnnotated(JavaParameter parameter, String annotation) {
        return parameter.getAnnotations().stream()
                .anyMatch(a -> a.getRawType().getName().equals(annotation));
    }

    /** The declared return type plus, when it is parameterized, its type arguments. */
    private static java.util.List<JavaClass> returnedTypes(JavaMethod method) {
        JavaType returned = method.getReturnType();
        java.util.List<JavaClass> types = new java.util.ArrayList<>();
        types.add(returned.toErasure());
        if (returned instanceof JavaParameterizedType parameterized) {
            parameterized.getActualTypeArguments()
                    .forEach(argument -> types.add(argument.toErasure()));
        }
        return types;
    }
}
