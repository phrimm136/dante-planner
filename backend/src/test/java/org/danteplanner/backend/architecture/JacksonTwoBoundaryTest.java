package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.architecture.fixture.JacksonAnnotatedRecord;
import org.danteplanner.backend.architecture.fixture.JacksonTwoMapperFactory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonTwoBoundaryTest {

    static final ArchRule NO_JACKSON_TWO_DATABIND_OR_CORE =
            noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson.databind..", "com.fasterxml.jackson.core..")
                    .as("application code serializes with Jackson 3 (tools.jackson); only Jackson 2's annotations stay");

    @Test
    void production_WhenScanned_DependsOnNoJacksonTwoDatabindOrCore() {
        assertThatCode(() -> NO_JACKSON_TWO_DATABIND_OR_CORE.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void jacksonAnnotationUser_WhenScanned_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(JacksonAnnotatedRecord.class);

        assertThatCode(() -> NO_JACKSON_TWO_DATABIND_OR_CORE.check(fixture)).doesNotThrowAnyException();
    }

    @Test
    void jacksonTwoMapperUser_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(JacksonTwoMapperFactory.class);

        assertThatThrownBy(() -> NO_JACKSON_TWO_DATABIND_OR_CORE.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Method <" + JacksonTwoMapperFactory.class.getName() + ".mapper()>"
                        + " calls constructor <com.fasterxml.jackson.databind.ObjectMapper.<init>()>");
    }
}
