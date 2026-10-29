package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.architecture.fixture.consistency.SpringImportingChecker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistencyCheckerIsolationTest {

    private static final String CHECKER = "org.danteplanner.consistency";
    private static final String TEST_SUFFIX = "Test";

    private static final DescribedPredicate<JavaClass> CHECKER_CLASS = DescribedPredicate.describe(
            "are not the checker's own tests",
            (JavaClass candidate) -> !outermost(candidate).getSimpleName().endsWith(TEST_SUFFIX));

    static final ArchRule CHECKER_DEPENDS_ONLY_ON_JAVA_AND_JACKSON =
            classes()
                    .that(CHECKER_CLASS)
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "tools.jackson..", CHECKER + "..")
                    .as("the consistency checker depends only on java.*, Jackson and its own package");

    private static final JavaClasses CHECKER_CLASSES = new ClassFileImporter().importPackages(CHECKER);

    @Test
    void checker_WhenScanned_DependsOnlyOnJavaJacksonAndItself() {
        assertThatCode(() -> CHECKER_DEPENDS_ONLY_ON_JAVA_AND_JACKSON.check(CHECKER_CLASSES))
                .doesNotThrowAnyException();
    }

    @Test
    void checkerScan_WhenFiltered_KeepsTheCheckerAndDropsItsTests() {
        assertThat(CHECKER_CLASSES.stream().filter(CHECKER_CLASS).map(JavaClass::getSimpleName))
                .contains("ConsistencyChecker", "History", "HistoryLine")
                .doesNotContain("ConsistencyCheckerTest", "HistoryTest");
    }

    @Test
    void springImportingClass_WhenScanned_FailsOnceNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(SpringImportingChecker.class);

        assertThatThrownBy(() -> CHECKER_DEPENDS_ONLY_ON_JAVA_AND_JACKSON.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated (1 times)")
                .hasMessageContaining("Method <" + SpringImportingChecker.class.getName() + ".judge(java.lang.String)>"
                        + " calls method <org.springframework.util.StringUtils.hasText(java.lang.String)>");
    }

    private static JavaClass outermost(JavaClass candidate) {
        JavaClass current = candidate;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }
}
