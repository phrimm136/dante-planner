package org.danteplanner.backend.architecture;

import org.danteplanner.backend.architecture.fixture.planner.validation.floor.ServiceReachingFloorModule;
import org.danteplanner.backend.architecture.fixture.planner.validation.floor.SiblingReachingFloorModule;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

    private static final String FLOOR_MODULE = "..planner.validation.floor..";
    private static final String SUBJECT_PARENT = ".planner.validation.";
    private static final String FLOOR_SUBJECT = "floor";

    private static final DescribedPredicate<JavaClass> SIBLING_SUBJECT = DescribedPredicate.describe(
            "reside in a subject package under planner.validation other than floor",
            FloorModuleBoundaryTest::isSiblingSubject);

    static final ArchRule FLOOR_MODULE_KNOWS_NO_CALLERS =
            noClasses()
                    .that().resideInAPackage(FLOOR_MODULE)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..web..", "..controller..", "..repository..")
                    .as("the floor module depends on no service, web, controller or repository package");

    static final ArchRule FLOOR_MODULE_KNOWS_NO_SIBLING_SUBJECT =
            noClasses()
                    .that().resideInAPackage(FLOOR_MODULE)
                    .should().dependOnClassesThat(SIBLING_SUBJECT)
                    .as("the floor module depends on the validation root, never on a sibling subject package");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend.planner.validation.floor");

    @Test
    void floorModule_WhenScanned_DependsOnNoCallerPackage() {
        assertThatCode(() -> FLOOR_MODULE_KNOWS_NO_CALLERS.check(PRODUCTION)).doesNotThrowAnyException();
    }

    @Test
    void serviceReachingFloorClass_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ServiceReachingFloorModule.class);

        assertThatThrownBy(() -> FLOOR_MODULE_KNOWS_NO_CALLERS.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ServiceReachingFloorModule.class.getName());
    }

    @Test
    void floorModule_WhenScanned_DependsOnNoSiblingSubject() {
        assertThatCode(() -> FLOOR_MODULE_KNOWS_NO_SIBLING_SUBJECT.check(PRODUCTION)).doesNotThrowAnyException();
    }

    @Test
    void siblingReachingFloorClass_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(SiblingReachingFloorModule.class);

        assertThatThrownBy(() -> FLOOR_MODULE_KNOWS_NO_SIBLING_SUBJECT.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(SiblingReachingFloorModule.class.getName());
    }

    private static boolean isSiblingSubject(JavaClass target) {
        String packageName = target.getPackageName() + ".";
        int parent = packageName.indexOf(SUBJECT_PARENT);
        if (parent < 0) {
            return false;
        }
        String subject = packageName.substring(parent + SUBJECT_PARENT.length());
        return !subject.isEmpty() && !subject.startsWith(FLOOR_SUBJECT + ".");
    }
}
