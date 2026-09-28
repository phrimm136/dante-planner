package org.danteplanner.backend.architecture;

import org.danteplanner.backend.architecture.fixture.planner.floor.ServiceReachingFloorModule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The floor module knows no callers: it depends on no service, web, controller or repository
 * package (ADR 138).
 */
class FloorModuleBoundaryTest {

    static final ArchRule FLOOR_MODULE_KNOWS_NO_CALLERS =
            noClasses()
                    .that().resideInAPackage("..planner.floor..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..web..", "..controller..", "..repository..")
                    .as("the floor module depends on no service, web, controller or repository package");

    @Test
    @DisplayName("The floor module depends on no caller package")
    void floorModule_WhenScanned_DependsOnNoCallerPackage() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.danteplanner.backend.planner.floor");

        assertThatCode(() -> FLOOR_MODULE_KNOWS_NO_CALLERS.check(production)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A floor-module class reaching a service fails naming the class")
    void serviceReachingFloorClass_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ServiceReachingFloorModule.class);

        assertThatThrownBy(() -> FLOOR_MODULE_KNOWS_NO_CALLERS.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ServiceReachingFloorModule.class.getName());
    }
}
