package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * The rate-limit declaration rules, shared by the build gate and the scenarios that prove they
 * reject.
 *
 * <p>Held apart from their {@code @AnalyzeClasses} host so a test can evaluate the same rule
 * objects against fixtures: a rule asserted only against a passing codebase has never been shown
 * to reject anything.</p>
 */
final class RateLimitCoverageRule {

    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class,
            GetMapping.class,
            PostMapping.class,
            PutMapping.class,
            PatchMapping.class,
            DeleteMapping.class);

    static final ArchRule REQUEST_HANDLERS_DECLARE_A_RATE_LIMIT_POLICY =
            methods()
                    .that(areRequestHandlers())
                    .should(declareARateLimitPolicy())
                    .as("every request handler declares a rate-limit policy or an explicit exemption");

    static final ArchRule DECLARATIONS_MATCH_THEIR_POLICY_SHAPE =
            methods()
                    .that(areRequestHandlers())
                    .should(matchTheirPolicyShape())
                    .as("every rate-limit declaration names an endpoint exactly when its policy needs one");

    private RateLimitCoverageRule() {
    }

    /** Whether the method is dispatched to as a request handler. */
    static boolean isRequestHandler(JavaMethod method) {
        return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
    }

    private static DescribedPredicate<JavaMethod> areRequestHandlers() {
        return new DescribedPredicate<>("are request handlers") {
            @Override
            public boolean test(JavaMethod method) {
                return isRequestHandler(method);
            }
        };
    }

    /**
     * The exemption is accepted only on the method. A class-level exemption would cover handlers
     * added to that class later without anyone restating the decision.
     */
    private static ArchCondition<JavaMethod> declareARateLimitPolicy() {
        return new ArchCondition<>(
                "declare @RateLimited on the method or its controller, or @RateLimitExempt on the method") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(RateLimited.class)
                        || method.isAnnotatedWith(RateLimitExempt.class)
                        || method.getOwner().isAnnotatedWith(RateLimited.class)) {
                    return;
                }
                events.add(SimpleConditionEvent.violated(
                        method,
                        method.getFullName()
                                + " reaches its body with no rate-limit declaration: annotate the handler"
                                + " with @RateLimited, or state the exemption with @RateLimitExempt"));
            }
        };
    }

    /**
     * A caller-named policy charged without an endpoint fails at runtime as a 500; a self-naming
     * policy charged with one silently respells a live bucket key. Neither is visible in review.
     */
    private static ArchCondition<JavaMethod> matchTheirPolicyShape() {
        return new ArchCondition<>("name an endpoint exactly when the declared policy requires one") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                RateLimited declaration = governingDeclaration(method);
                if (declaration == null) {
                    return;
                }

                RateLimitPolicy policy = declaration.value();
                boolean namesEndpoint = !declaration.endpoint().isEmpty();

                if (policy.requiresCallerNamedEndpoint() && !namesEndpoint) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " declares " + policy
                                    + ", which keys its buckets per route, but names no endpoint"));
                } else if (!policy.requiresCallerNamedEndpoint() && namesEndpoint) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " declares " + policy + " with endpoint '"
                                    + declaration.endpoint()
                                    + "', but that policy names its own endpoint"));
                }
            }
        };
    }

    /**
     * @return the declaration governing the handler, or null when it is exempt or undeclared
     */
    static RateLimited governingDeclaration(JavaMethod method) {
        if (method.isAnnotatedWith(RateLimited.class)) {
            return method.getAnnotationOfType(RateLimited.class);
        }
        if (method.isAnnotatedWith(RateLimitExempt.class)) {
            return null;
        }
        return method.getOwner().isAnnotatedWith(RateLimited.class)
                ? method.getOwner().getAnnotationOfType(RateLimited.class)
                : null;
    }
}
