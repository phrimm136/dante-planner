package org.danteplanner.backend.architecture;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.danteplanner.backend.architecture.fixture.UnadmittedFilterRebuilder;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.searchindex.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static org.assertj.core.api.Assertions.assertThat;

class ContentWritersAdmitFloorsTest {

    private static final Set<String> ADMISSION_PATH = Set.of(
            PlannerContentValidator.class.getName(),
            FloorRules.class.getName(),
            PlannerContentEntityExtractor.class.getName());

    private static final JavaClasses PRODUCTION = ImportedClasses.production();

    @Test
    void contentWriters_WhenScanned_DependOnTheAdmissionPath() {
        assertThat(unadmittedWriters(PRODUCTION))
                .as("these rebuild planner_entity_filter without depending on "
                        + "PlannerContentValidator, FloorRules or PlannerContentEntityExtractor; admit the floors first")
                .isEmpty();
    }

    @Test
    void contentWriters_WhenScanned_IncludeEveryKnownWriter() {
        assertThat(topLevelNames(PRODUCTION, ContentWritersAdmitFloorsTest::writes)).containsExactlyInAnyOrder(
                PlannerCatalogService.class.getName());
    }

    @Test
    void unadmittedFilterRebuilder_WhenScanned_IsNamed() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UnadmittedFilterRebuilder.class);

        assertThat(unadmittedWriters(fixture)).containsExactly(UnadmittedFilterRebuilder.class.getName());
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
        return call.getTargetOwner().getName().equals(PlannerEntityFilterRepository.class.getName())
                && call.getName().equals("rebuildPlannerFilters");
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
