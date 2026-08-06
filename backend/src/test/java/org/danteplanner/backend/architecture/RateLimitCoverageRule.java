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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * The coverage rule shared by the build gate and the scenario that proves it fails.
 *
 * <p>Held apart from its {@code @AnalyzeClasses} host so a test can evaluate the same rule object
 * against a fixture: a rule asserted only against a passing codebase has never been shown to
 * reject anything.</p>
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

    private RateLimitCoverageRule() {
    }

    private static DescribedPredicate<JavaMethod> areRequestHandlers() {
        return new DescribedPredicate<>("are request handlers") {
            @Override
            public boolean test(JavaMethod method) {
                return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
            }
        };
    }

    private static ArchCondition<JavaMethod> declareARateLimitPolicy() {
        return new ArchCondition<>("declare @RateLimited or @RateLimitExempt, on the method or its controller") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (declaresAPolicy(method) || declaresAPolicy(method.getOwner())) {
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

    private static boolean declaresAPolicy(CanBeAnnotated target) {
        return target.isAnnotatedWith(RateLimited.class) || target.isAnnotatedWith(RateLimitExempt.class);
    }
}
