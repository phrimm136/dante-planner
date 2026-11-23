package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import org.danteplanner.fixture.ratelimit.UnvalidatedPropertiesFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationPropertiesRuleTest {

    static final ArchRule CONFIGURATION_PROPERTIES_ARE_VALIDATED =
            classes()
                    .that().areAnnotatedWith(ConfigurationProperties.class)
                    .should().beAnnotatedWith(Validated.class)
                    .as("every @ConfigurationProperties class is @Validated, so a bad value fails at startup");

    @Test
    void production_WhenScanned_ValidatesEveryConfigurationPropertiesClass() {
        assertThatCode(() -> CONFIGURATION_PROPERTIES_ARE_VALIDATED.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItIsNotValidated_FailsNamingIt() {
        assertThatThrownBy(() -> CONFIGURATION_PROPERTIES_ARE_VALIDATED.check(
                new ClassFileImporter().importClasses(UnvalidatedPropertiesFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(UnvalidatedPropertiesFixture.class.getName());
    }
}
