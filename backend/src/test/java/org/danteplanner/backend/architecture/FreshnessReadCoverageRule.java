package org.danteplanner.backend.architecture;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.FreshnessPending;
import org.danteplanner.backend.shared.freshness.ScopedRead;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

final class FreshnessReadCoverageRule {

    private static final String SCOPED = "a ScopedRead return";
    private static final String INNER = "propagation = MANDATORY";
    private static final String PENDING = "@FreshnessPending";
    private static final String EXEMPT = "@FreshnessExempt";

    static final ArchRule READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ =
            methods()
                    .that(areReadOnlyTransactionalOrReturnScopedRead())
                    .should(declareExactlyOneFreshnessRead())
                    .as("every @Transactional(readOnly = true) method declares exactly one of " + SCOPED + ", "
                            + INNER + ", " + PENDING + "(reason) or " + EXEMPT + "(reason), with a non-blank reason")
                    .allowEmptyShould(true);

    private FreshnessReadCoverageRule() {
    }

    private static DescribedPredicate<JavaMethod> areReadOnlyTransactionalOrReturnScopedRead() {
        return DescribedPredicate.describe("are @Transactional(readOnly = true) or return a ScopedRead",
                method -> isReadOnly(method) || returnsScopedRead(method));
    }

    private static boolean isReadOnly(JavaMethod method) {
        return effectiveTransactional(method) != null && effectiveTransactional(method).readOnly();
    }

    private static boolean returnsScopedRead(JavaMethod method) {
        return method.getRawReturnType().isEquivalentTo(ScopedRead.class);
    }

    private static boolean isReadOnlyOnTheMethod(JavaMethod method) {
        return method.isAnnotatedWith(Transactional.class) && method.getAnnotationOfType(Transactional.class).readOnly();
    }

    private static Transactional effectiveTransactional(JavaMethod method) {
        if (method.isAnnotatedWith(Transactional.class)) {
            return method.getAnnotationOfType(Transactional.class);
        }
        if (method.getModifiers().contains(JavaModifier.PUBLIC) && !method.getModifiers().contains(JavaModifier.STATIC)
                && method.getOwner().isAnnotatedWith(Transactional.class)) {
            return method.getOwner().getAnnotationOfType(Transactional.class);
        }
        return null;
    }

    private static List<String> declarationsOn(JavaMethod method) {
        List<String> declared = new ArrayList<>();
        if (returnsScopedRead(method)) {
            declared.add(SCOPED);
        }
        if (effectiveTransactional(method).propagation() == Propagation.MANDATORY) {
            declared.add(INNER);
        }
        if (method.isAnnotatedWith(FreshnessPending.class)) {
            declared.add(PENDING);
        }
        if (method.isAnnotatedWith(FreshnessExempt.class)) {
            declared.add(EXEMPT);
        }
        return declared;
    }

    private static boolean hasBlankReason(JavaMethod method) {
        return (method.isAnnotatedWith(FreshnessPending.class)
                        && method.getAnnotationOfType(FreshnessPending.class).reason().isBlank())
                || (method.isAnnotatedWith(FreshnessExempt.class)
                        && method.getAnnotationOfType(FreshnessExempt.class).reason().isBlank());
    }

    private static ArchCondition<JavaMethod> declareExactlyOneFreshnessRead() {
        return new ArchCondition<>("declare exactly one freshness read declaration") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (returnsScopedRead(method) && !isReadOnlyOnTheMethod(method)) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " returns a ScopedRead without @Transactional(readOnly = true) on the method:"
                            + " put the annotation on the method, the only place the freshness advice matches it"));
                }
                if (!isReadOnly(method)) {
                    return;
                }
                List<String> declared = declarationsOn(method);
                if (declared.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " declares no freshness read: return a ScopedRead, join with propagation = MANDATORY,"
                            + " or annotate it with @FreshnessPending(reason) or @FreshnessExempt(reason)"));
                } else if (declared.size() > 1) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " declares " + declared + "; a readOnly method declares exactly one"));
                }
                if (hasBlankReason(method)) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " names a blank freshness reason"));
                }
            }
        };
    }
}
