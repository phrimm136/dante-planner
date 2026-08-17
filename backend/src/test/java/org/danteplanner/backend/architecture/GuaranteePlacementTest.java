package org.danteplanner.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.danteplanner.backend.moderation.service.ModerationPolicy;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.shared.exception.DomainException;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Where a guarantee is allowed to be enforced.
 *
 * <p>A service body reads as orchestration: it loads, it delegates, it persists. The business rule
 * it used to state inline lives in a validator component, so a rule's home is decided by its kind
 * rather than by how many callers happen to need it. These rules hold at zero violations against
 * the tree as it stands; a new violation means new code drifted.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class GuaranteePlacementTest {

    private static final String VALIDATED = "org.springframework.validation.annotation.Validated";
    private static final String CONFIGURATION_PROPERTIES =
            "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String METHOD_VALIDATION_POST_PROCESSOR =
            "org.springframework.validation.beanvalidation.MethodValidationPostProcessor";
    private static final String CONSTRAINT_PACKAGE = "jakarta.validation.constraints.";

    /**
     * The two classes that answer an authority question for a whole feature and are named for that
     * job rather than for a layer. Every other rule home is a {@code ..validation..} component.
     */
    private static final Set<String> AUTHORITY_COMPONENTS = Set.of(
            PlannerAccessGuard.class.getName(),
            ModerationPolicy.class.getName());

    /**
     * A domain exception is the wire form of a refused business rule, so constructing one is
     * stating the rule. Only a validator component, an access guard, or a moderation policy may.
     *
     * <p>Carved out: construction inside a lambda, which is the {@code orElseThrow} supplier a
     * lookup hands its own absence. That is the service loading a row, not deciding one.</p>
     */
    @ArchTest
    static final ArchRule domain_exceptions_are_raised_by_rule_components =
            classes()
                    .that().resideInAPackage("..service..")
                    .and().resideOutsideOfPackage("..validation..")
                    .and(are(not(anAuthorityComponent())))
                    .should(constructNoDomainExceptionOutsideALambda())
                    .as("business rules live in validator components: a service may not construct a"
                            + " DomainException outside an orElseThrow supplier");

    /**
     * Method-validation AOP runs constraints on an internal call boundary, where a violation
     * surfaces as a {@code ConstraintViolationException} the advice layer collapses to one generic
     * wire code. The {@code @Valid} request pipeline is the only place an annotation executes.
     *
     * <p>{@code @Validated} on a {@code @ConfigurationProperties} class is a different mechanism —
     * binding-time validation at startup, no proxy, no request in flight — and stays permitted.</p>
     */
    @ArchTest
    static final ArchRule no_method_validation_aop =
            noClasses()
                    .that(are(not(configurationPropertiesBinding())))
                    .should().beAnnotatedWith(VALIDATED)
                    .orShould().dependOnClassesThat()
                    .haveFullyQualifiedName(METHOD_VALIDATION_POST_PROCESSOR)
                    .as("no method-validation AOP: @Validated only on @ConfigurationProperties"
                            + " bindings, and no MethodValidationPostProcessor bean");

    @ArchTest
    static final ArchRule no_method_level_validated =
            noMethods()
                    .should().beAnnotatedWith(VALIDATED)
                    .as("@Validated on a method turns on the same AOP the class-level ban rejects");

    /**
     * Request input is validated by Jakarta annotations on the request DTO, which is where the
     * {@code @Valid} pipeline runs them. An annotation anywhere else either never executes or
     * executes on a boundary nobody chose.
     *
     * <p>Scoped to the whole {@code jakarta.validation.constraints} package rather than to a subset:
     * no entity in this tree carries one, so nothing is being grandfathered. The single residual is
     * the {@code @ConfigurationProperties} binding, whose constraints are the environment's DTO
     * contract and run at startup.</p>
     */
    @ArchTest
    static final ArchRule constraints_live_on_request_dtos =
            classes()
                    .that().resideOutsideOfPackage("..dto..")
                    .and(are(not(configurationPropertiesBinding())))
                    .should(carryNoJakartaConstraint())
                    .as("jakarta.validation.constraints annotations live on request DTOs only");

    private static ArchCondition<JavaClass> constructNoDomainExceptionOutsideALambda() {
        return new ArchCondition<>("construct no DomainException outside a lambda") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaConstructorCall call : javaClass.getConstructorCallsFromSelf()) {
                    if (!call.getTargetOwner().isAssignableTo(DomainException.class)
                            || call.isDeclaredInLambda()) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(javaClass,
                            call.getOrigin().getFullName() + " constructs "
                                    + call.getTargetOwner().getName()
                                    + "; move the rule to a validator component"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> carryNoJakartaConstraint() {
        return new ArchCondition<>("carry no jakarta.validation.constraints annotation") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (String constraint : constraintsOn(javaClass)) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " carries " + constraint
                                    + "; constraints belong on the request DTO the @Valid pipeline runs"));
                }
            }
        };
    }

    private static Set<String> constraintsOn(JavaClass javaClass) {
        Stream<JavaAnnotation<?>> onMembers = Stream.concat(
                javaClass.getFields().stream().flatMap(field -> field.getAnnotations().stream()),
                javaClass.getCodeUnits().stream().flatMap(GuaranteePlacementTest::annotationsOn));
        return Stream.concat(javaClass.getAnnotations().stream(), onMembers)
                .map(annotation -> annotation.getRawType().getName())
                .filter(name -> name.startsWith(CONSTRAINT_PACKAGE))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Stream<JavaAnnotation<?>> annotationsOn(JavaCodeUnit codeUnit) {
        Stream<JavaAnnotation<?>> onParameters = codeUnit.getParameters().stream()
                .flatMap(parameter -> parameter.getAnnotations().stream());
        return Stream.concat(codeUnit.getAnnotations().stream(), onParameters);
    }

    /** A class is a configuration-properties binding when it, or a class enclosing it, is bound. */
    private static DescribedPredicate<JavaClass> configurationPropertiesBinding() {
        return DescribedPredicate.describe("a @ConfigurationProperties binding", javaClass -> {
            for (Optional<JavaClass> current = Optional.of(javaClass);
                    current.isPresent();
                    current = current.get().getEnclosingClass()) {
                if (current.get().isAnnotatedWith(CONFIGURATION_PROPERTIES)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static DescribedPredicate<JavaClass> anAuthorityComponent() {
        return DescribedPredicate.describe("an access guard or moderation policy",
                javaClass -> AUTHORITY_COMPONENTS.contains(javaClass.getName()));
    }

    private static DescribedPredicate<JavaClass> are(DescribedPredicate<JavaClass> predicate) {
        return predicate;
    }

    private static DescribedPredicate<JavaClass> not(DescribedPredicate<JavaClass> predicate) {
        return DescribedPredicate.not(predicate);
    }
}
