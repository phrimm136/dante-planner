package org.danteplanner.backend.architecture;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.danteplanner.backend.architecture.fixture.UnadmittedContentWriter;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.floor.FloorRules;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThat;

class ContentWritersAdmitFloorsTest {

    private static final String CONTENT_BUILDER = PlannerContent.class.getName() + "$PlannerContentBuilder";

    private static final Set<String> ADMISSION_PATH = Set.of(
            PlannerContentValidator.class.getName(),
            FloorRules.class.getName(),
            PlannerContentEntityExtractor.class.getName());

    private static final Set<String> FROZEN_UNADMITTED_WRITERS = Set.of(
            PlannerFilterService.class.getName());

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    @Test
    @DisplayName("Every production class storing content or rebuilding the entity filter depends on the admission path")
    void contentWriters_WhenScanned_DependOnTheAdmissionPathUnlessFrozen() {
        Set<String> unfrozen = new TreeSet<>(unadmittedWriters(PRODUCTION));
        unfrozen.removeAll(FROZEN_UNADMITTED_WRITERS);

        assertThat(unfrozen)
                .as("these store planner content or rebuild planner_entity_filter without depending on "
                        + "PlannerContentValidator, FloorRules or PlannerContentEntityExtractor; admit the floors first")
                .isEmpty();
    }

    @Test
    @DisplayName("Every frozen unadmitted writer still writes without the admission path")
    void frozenUnadmittedWriters_WhenScanned_StillWriteWithoutAdmission() {
        Set<String> stale = new TreeSet<>(FROZEN_UNADMITTED_WRITERS);
        stale.removeAll(unadmittedWriters(PRODUCTION));

        assertThat(stale)
                .as("these no longer write without admission, so the entry excuses nothing; delete it")
                .isEmpty();
    }

    @Test
    @DisplayName("The writer selector finds today's content and index writers")
    void contentWriters_WhenScanned_IncludeEveryKnownWriter() {
        assertThat(topLevelNames(PRODUCTION, ContentWritersAdmitFloorsTest::writes)).containsExactlyInAnyOrder(
                PlannerCommandService.class.getName(),
                PlannerPublishingService.class.getName(),
                PlannerFilterService.class.getName());
    }

    @Test
    @DisplayName("A class storing content without the admission path is named by the rule")
    void unadmittedContentWriter_WhenScanned_IsNamed() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UnadmittedContentWriter.class);

        assertThat(unadmittedWriters(fixture)).containsExactly(UnadmittedContentWriter.class.getName());
    }

    private static Set<String> unadmittedWriters(JavaClasses classes) {
        Set<String> writers = topLevelNames(classes, ContentWritersAdmitFloorsTest::writes);
        writers.removeAll(topLevelNames(classes, ContentWritersAdmitFloorsTest::dependsOnAdmission));
        return writers;
    }

    private static Set<String> topLevelNames(JavaClasses classes, Predicate<JavaClass> predicate) {
        Set<String> names = new TreeSet<>();
        classes.stream().filter(predicate).forEach(javaClass -> names.add(topLevel(javaClass.getName())));
        return names;
    }

    private static boolean writes(JavaClass javaClass) {
        return javaClass.getMethodCallsFromSelf().stream().anyMatch(ContentWritersAdmitFloorsTest::isWrite);
    }

    private static boolean isWrite(JavaMethodCall call) {
        String owner = call.getTargetOwner().getName();
        String name = call.getName();
        return (owner.equals(PlannerContent.class.getName()) && name.equals("setContent"))
                || (owner.equals(CONTENT_BUILDER) && name.equals("content"))
                || (owner.equals(PlannerEntityFilterRepository.class.getName()) && name.equals("rebuildPlannerFilters"));
    }

    private static boolean dependsOnAdmission(JavaClass javaClass) {
        return javaClass.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> ADMISSION_PATH.contains(dependency.getTargetClass().getName()));
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }
}
