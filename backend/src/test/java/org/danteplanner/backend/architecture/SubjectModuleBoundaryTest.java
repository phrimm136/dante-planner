package org.danteplanner.backend.architecture;

import java.util.Map;

import org.danteplanner.backend.architecture.fixture.planner.validation.ego.EgoSubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.egogift.EgoGiftSubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.identity.IdentitySubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.sinner.SinnerSubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.skill.SkillSubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.startbuff.StartBuffSubjectLeak;
import org.danteplanner.backend.architecture.fixture.planner.validation.startgift.StartGiftSubjectLeak;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * Each content subject under planner validation knows no callers and no sibling subject: it
 * depends on the validation root and game data only (ADR 138).
 */
class SubjectModuleBoundaryTest {

    private static final String SUBJECT_PARENT = ".planner.validation.";
    private static final String VALIDATION_PACKAGE = "org.danteplanner.backend.planner.validation.";

    private static final Map<String, Class<?>> LEAKS = Map.of(
            "sinner", SinnerSubjectLeak.class,
            "identity", IdentitySubjectLeak.class,
            "ego", EgoSubjectLeak.class,
            "skill", SkillSubjectLeak.class,
            "egogift", EgoGiftSubjectLeak.class,
            "startbuff", StartBuffSubjectLeak.class,
            "startgift", StartGiftSubjectLeak.class);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sinner", "identity", "ego", "skill", "egogift", "startbuff", "startgift"})
    void subjectModule_WhenScanned_DependsOnNoCallerPackage(String subject) {
        assertThatCode(() -> knowsNoCallers(subject).check(production(subject))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sinner", "identity", "ego", "skill", "egogift", "startbuff", "startgift"})
    void subjectModule_WhenScanned_DependsOnNoSiblingSubject(String subject) {
        assertThatCode(() -> knowsNoSiblingSubject(subject).check(production(subject))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sinner", "identity", "ego", "skill", "egogift", "startbuff", "startgift"})
    void callerReachingSubjectClass_WhenScanned_FailsNamingTheClass(String subject) {
        Class<?> leak = LEAKS.get(subject);

        assertThatThrownBy(() -> knowsNoCallers(subject).check(new ClassFileImporter().importClasses(leak)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(leak.getName());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sinner", "identity", "ego", "skill", "egogift", "startbuff", "startgift"})
    void siblingReachingSubjectClass_WhenScanned_FailsNamingTheClass(String subject) {
        Class<?> leak = LEAKS.get(subject);

        assertThatThrownBy(() -> knowsNoSiblingSubject(subject).check(new ClassFileImporter().importClasses(leak)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(leak.getName());
    }

    private static ArchRule knowsNoCallers(String subject) {
        return noClasses()
                .that().resideInAPackage(subjectPackage(subject))
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..web..", "..controller..", "..repository..")
                .as("the " + subject + " subject depends on no service, web, controller or repository package");
    }

    private static ArchRule knowsNoSiblingSubject(String subject) {
        return noClasses()
                .that().resideInAPackage(subjectPackage(subject))
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "reside in a subject package under planner.validation other than " + subject,
                        (JavaClass target) -> isSiblingSubject(target, subject)))
                .as("the " + subject + " subject depends on the validation root, never on a sibling subject package");
    }

    private static JavaClasses production(String subject) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(VALIDATION_PACKAGE + subject);
    }

    private static String subjectPackage(String subject) {
        return "..planner.validation." + subject + "..";
    }

    private static boolean isSiblingSubject(JavaClass target, String subject) {
        String packageName = target.getPackageName() + ".";
        int parent = packageName.indexOf(SUBJECT_PARENT);
        if (parent < 0) {
            return false;
        }
        String targetSubject = packageName.substring(parent + SUBJECT_PARENT.length());
        return !targetSubject.isEmpty() && !targetSubject.startsWith(subject + ".");
    }
}
