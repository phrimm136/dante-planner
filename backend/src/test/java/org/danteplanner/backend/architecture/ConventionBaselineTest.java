package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
}
