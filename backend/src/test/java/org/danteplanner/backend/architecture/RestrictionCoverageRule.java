package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictedByState;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

final class RestrictionCoverageRule {

    private static final List<Class<? extends Annotation>> WRITE_MAPPINGS = List.of(
            PostMapping.class,
            PutMapping.class,
            PatchMapping.class,
            DeleteMapping.class);

    private static final Set<RequestMethod> WRITE_METHODS = Set.of(
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE);

    private static final List<Class<? extends Annotation>> DECLARATIONS = List.of(
            Restricted.class,
            RestrictionExempt.class,
            RestrictedByState.class);

    static final ArchRule WRITE_HANDLERS_DECLARE_EXACTLY_ONE_RESTRICTION =
            methods()
                    .that(areWriteHandlers())
                    .should(declareExactlyOneRestriction())
                    .as("every write handler declares exactly one of @Restricted, @RestrictionExempt(reason)"
                            + " or @RestrictedByState");

    static final ArchRule EXEMPTIONS_NAME_A_REASON =
            methods()
                    .that(areWriteHandlers())
                    .should(nameAReasonWhenExempt())
                    .as("every @RestrictionExempt names a non-blank reason");

    static final ArchRule READ_HANDLERS_DECLARE_NO_RESTRICTION =
            methods()
                    .that(areReadHandlers())
                    .should(declareNoRestriction())
                    .as("no read handler declares a restriction");

    private RestrictionCoverageRule() {
    }

    private static DescribedPredicate<JavaMethod> areWriteHandlers() {
        return DescribedPredicate.describe("are write handlers", RestrictionCoverageRule::isWriteHandler);
    }

    private static DescribedPredicate<JavaMethod> areReadHandlers() {
        return DescribedPredicate.describe("are read handlers",
                method -> !isWriteHandler(method)
                        && (method.isAnnotatedWith(GetMapping.class) || method.isAnnotatedWith(RequestMapping.class)));
    }

    private static boolean isWriteHandler(JavaMethod method) {
        if (WRITE_MAPPINGS.stream().anyMatch(method::isAnnotatedWith)) {
            return true;
        }
        if (!method.isAnnotatedWith(RequestMapping.class)) {
            return false;
        }
        RequestMethod[] mapped = method.getAnnotationOfType(RequestMapping.class).method();
        return mapped.length == 0 || List.of(mapped).stream().anyMatch(WRITE_METHODS::contains);
    }

    private static List<String> declarationsOn(JavaMethod method) {
        return DECLARATIONS.stream()
                .filter(method::isAnnotatedWith)
                .map(declaration -> "@" + declaration.getSimpleName())
                .toList();
    }

    private static ArchCondition<JavaMethod> declareExactlyOneRestriction() {
        return new ArchCondition<>("declare exactly one restriction annotation") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                List<String> declared = declarationsOn(method);
                if (declared.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " declares no restriction: annotate it with @Restricted, @RestrictedByState,"
                            + " or @RestrictionExempt(reason)"));
                } else if (declared.size() > 1) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " declares " + declared + "; a write handler declares exactly one"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> nameAReasonWhenExempt() {
        return new ArchCondition<>("name a non-blank reason when exempt") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(RestrictionExempt.class)
                        && method.getAnnotationOfType(RestrictionExempt.class).reason().isBlank()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " is @RestrictionExempt with a blank reason"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> declareNoRestriction() {
        return new ArchCondition<>("declare no restriction annotation") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                List<String> declared = declarationsOn(method);
                if (!declared.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " is a read handler but declares " + declared));
                }
            }
        };
    }
}
