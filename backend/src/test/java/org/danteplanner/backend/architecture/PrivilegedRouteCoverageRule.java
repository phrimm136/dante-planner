package org.danteplanner.backend.architecture;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

final class PrivilegedRouteCoverageRule {

    private static final List<String> PRIVILEGED_PACKAGES = List.of(
            "org.danteplanner.backend.moderation.service",
            "org.danteplanner.backend.admin.service");

    private static final List<String> PRIVILEGED_PREFIXES = List.of("/api/moderation", "/api/admin");

    private static final List<String> MAPPING_ANNOTATIONS = Stream.of(
                    RequestMapping.class,
                    GetMapping.class,
                    PostMapping.class,
                    PutMapping.class,
                    PatchMapping.class,
                    DeleteMapping.class)
            .map(Class::getName)
            .toList();

    static final ArchRule PRIVILEGED_HANDLERS_LIVE_UNDER_PRIVILEGED_PREFIXES =
            methods()
                    .should(beMappedUnderAPrivilegedPrefixWhenTheirControllerReachesAPrivilegedService())
                    .as("every request handler whose controller depends on a moderation or admin service"
                            + " is mapped under /api/moderation or /api/admin");

    private PrivilegedRouteCoverageRule() {
    }

    private static ArchCondition<JavaMethod> beMappedUnderAPrivilegedPrefixWhenTheirControllerReachesAPrivilegedService() {
        return new ArchCondition<>("be mapped under a privileged prefix when the controller reaches a privileged service") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Optional<JavaAnnotation<?>> mapping = mappingOf(method);
                if (mapping.isEmpty() || !reachesAPrivilegedService(method.getOwner())) {
                    return;
                }
                List<String> strays = fullPaths(method.getOwner(), mapping.get()).stream()
                        .filter(path -> !underAPrivilegedPrefix(path))
                        .toList();
                if (!strays.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()
                            + " reaches a moderation or admin service but is mapped at " + strays
                            + ", outside the routes that re-read the actor's row"));
                }
            }
        };
    }

    private static Optional<JavaAnnotation<?>> mappingOf(HasAnnotations<?> element) {
        return element.getAnnotations().stream()
                .<JavaAnnotation<?>>map(annotation -> annotation)
                .filter(annotation -> MAPPING_ANNOTATIONS.contains(annotation.getRawType().getName()))
                .findFirst();
    }

    private static boolean reachesAPrivilegedService(JavaClass controller) {
        return controller.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getPackageName())
                .anyMatch(PRIVILEGED_PACKAGES::contains);
    }

    private static List<String> fullPaths(JavaClass controller, JavaAnnotation<?> mapping) {
        List<String> prefixes = mappingOf(controller).map(PrivilegedRouteCoverageRule::pathsOf).orElse(List.of(""));
        List<String> suffixes = pathsOf(mapping);
        return prefixes.stream()
                .flatMap(prefix -> suffixes.stream().map(suffix -> prefix + suffix))
                .toList();
    }

    private static List<String> pathsOf(JavaAnnotation<?> mapping) {
        List<String> paths = Stream.of("value", "path")
                .flatMap(property -> mapping.get(property).stream())
                .flatMap(values -> Stream.of((String[]) values))
                .distinct()
                .toList();
        return paths.isEmpty() ? List.of("") : paths;
    }

    private static boolean underAPrivilegedPrefix(String path) {
        return PRIVILEGED_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
