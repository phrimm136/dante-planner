package org.danteplanner.backend.architecture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class MockPortRule {

    static final ArchRule MOCKS_ONLY_AT_PORTS =
            classes()
                    .should(mockOnlyInfrastructurePorts())
                    .as("a @Mock or @Spy field or parameter has an infrastructure-port type: a Spring Data"
                            + " repository or a type outside org.danteplanner; every other type of this codebase"
                            + " takes part as a real instance or a support fake");

    static final ArchRule MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION =
            classes()
                    .should(createMocksOnlyThroughMockitoExtension())
                    .as("a class with @Mock or @Spy fields or parameters is extended with MockitoExtension and"
                            + " with nothing else that creates mocks");

    private static final String PROJECT_PACKAGE = "org.danteplanner";

    private MockPortRule() {
    }

    private static ArchCondition<JavaClass> mockOnlyInfrastructurePorts() {
        return new ArchCondition<>("mock only infrastructure ports") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                mockSites(clazz).forEach(site -> nonPortRole(site.type()).ifPresent(role -> events.add(
                        SimpleConditionEvent.violated(site.member(), site.name() + " mocks "
                                + site.type().getName() + ", " + role))));
            }
        };
    }

    private static ArchCondition<JavaClass> createMocksOnlyThroughMockitoExtension() {
        return new ArchCondition<>("create mocks only through MockitoExtension") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (mockSites(clazz).isEmpty()) {
                    return;
                }
                Set<String> extensions = extensionsOf(clazz);
                if (!extensions.contains(MockitoExtension.class.getName())) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " declares @Mock or @Spy fields or parameters without MockitoExtension"));
                }
                if (extensions.contains(SpringExtension.class.getName())) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " is also extended with SpringExtension"));
                }
                if (opensMocksByHand(clazz)) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " opens its mocks through MockitoAnnotations"));
                }
            }
        };
    }

    private static List<MockSite> mockSites(JavaClass clazz) {
        List<MockSite> sites = new ArrayList<>();
        for (JavaField field : clazz.getFields()) {
            if (isMock(field)) {
                sites.add(new MockSite(field, field.getFullName(), field.getRawType()));
            }
        }
        for (JavaCodeUnit codeUnit : clazz.getCodeUnits()) {
            for (JavaParameter parameter : codeUnit.getParameters()) {
                if (isMock(parameter)) {
                    sites.add(new MockSite(parameter, codeUnit.getFullName() + " parameter " + parameter.getIndex(),
                            parameter.getRawType()));
                }
            }
        }
        return sites;
    }

    private static boolean isMock(CanBeAnnotated member) {
        return member.isAnnotatedWith(Mock.class) || member.isAnnotatedWith(Spy.class);
    }

    private static Optional<String> nonPortRole(JavaClass type) {
        if (!isProjectType(type) || type.isAssignableTo(Repository.class)) {
            return Optional.empty();
        }
        if (type.isMetaAnnotatedWith(Component.class)) {
            return Optional.of("a Spring bean");
        }
        if (type.isInterface() && type.getAllSubclasses().stream()
                .anyMatch(implementation -> implementation.isMetaAnnotatedWith(Component.class))) {
            return Optional.of("an interface a Spring bean implements");
        }
        return Optional.of("a type of this codebase that is not a Spring Data repository");
    }

    private static boolean isProjectType(JavaClass type) {
        String packageName = type.getPackageName();
        return packageName.equals(PROJECT_PACKAGE) || packageName.startsWith(PROJECT_PACKAGE + ".");
    }

    private record MockSite(Object member, String name, JavaClass type) {
    }

    private static Set<String> extensionsOf(JavaClass clazz) {
        Set<String> extensions = new HashSet<>();
        Set<String> visitedAnnotations = new HashSet<>();
        for (JavaClass current = clazz; current != null; current = extensionSource(current)) {
            collectExtensions(current, extensions, visitedAnnotations);
            current.getAllRawSuperclasses()
                    .forEach(superclass -> collectExtensions(superclass, extensions, visitedAnnotations));
        }
        return extensions;
    }

    private static JavaClass extensionSource(JavaClass clazz) {
        return clazz.isInnerClass() ? clazz.getEnclosingClass().orElse(null) : null;
    }

    private static void collectExtensions(HasAnnotations<?> annotated, Set<String> extensions,
            Set<String> visitedAnnotations) {
        for (JavaAnnotation<?> annotation : annotated.getAnnotations()) {
            JavaClass type = annotation.getRawType();
            if (type.isEquivalentTo(ExtendWith.class)) {
                addExtendWith(annotation, extensions);
            } else if (type.isEquivalentTo(Extensions.class)) {
                annotation.get("value").ifPresent(value -> {
                    for (Object extendWith : (Object[]) value) {
                        addExtendWith((JavaAnnotation<?>) extendWith, extensions);
                    }
                });
            }
            if (visitedAnnotations.add(type.getName())) {
                collectExtensions(type, extensions, visitedAnnotations);
            }
        }
    }

    private static void addExtendWith(JavaAnnotation<?> extendWith, Set<String> extensions) {
        extendWith.get("value").ifPresent(value -> {
            for (Object extension : (Object[]) value) {
                extensions.add(((JavaClass) extension).getName());
            }
        });
    }

    private static boolean opensMocksByHand(JavaClass clazz) {
        return clazz.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(MockitoAnnotations.class));
    }
}
