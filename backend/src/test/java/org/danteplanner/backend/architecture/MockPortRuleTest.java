package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.mockportfixture.BeanMockFixture;
import org.danteplanner.mockportfixture.FixtureSeam;
import org.danteplanner.mockportfixture.FixtureService;
import org.danteplanner.mockportfixture.FixtureValue;
import org.danteplanner.mockportfixture.OpenMocksFixture;
import org.danteplanner.mockportfixture.ParameterMockFixture;
import org.danteplanner.mockportfixture.PortMockFixture;
import org.danteplanner.mockportfixture.SettingsMockFixture;
import org.danteplanner.mockportfixture.SpringExtensionMockFixture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPortRuleTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("org.danteplanner.mockportfixture");

    @Test
    void mocksOnlyAtPorts_WhenAServiceASeamAndAValueAreMocked_FailsNamingEachFieldAndParameter() {
        JavaClasses beanMocks = FIXTURES.that(belongToAnyOf(BeanMockFixture.class));
        String fixture = BeanMockFixture.class.getName();

        assertThatThrownBy(() -> MockPortRule.MOCKS_ONLY_AT_PORTS.check(beanMocks))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + ".service mocks " + FixtureService.class.getName() + ", a Spring bean")
                .hasMessageContaining(fixture + ".seam mocks " + FixtureSeam.class.getName()
                        + ", an interface a Spring bean implements")
                .hasMessageContaining(fixture + ".value mocks " + FixtureValue.class.getName()
                        + ", a type of this codebase that is not a Spring Data repository")
                .hasMessageContaining(fixture + ".<init>(" + FixtureSeam.class.getName() + ") parameter 0 mocks "
                        + FixtureSeam.class.getName() + ", an interface a Spring bean implements")
                .hasMessageContaining(fixture + ".exercise(" + FixtureService.class.getName()
                        + ") parameter 0 mocks " + FixtureService.class.getName() + ", a Spring bean");
    }

    @Test
    void mocksOnlyAtPorts_WhenOnlyARepositoryAndOutsideTypesAreMocked_Passes() {
        JavaClasses portMocks = FIXTURES.that(belongToAnyOf(PortMockFixture.class, SettingsMockFixture.class));

        assertThatCode(() -> MockPortRule.MOCKS_ONLY_AT_PORTS.check(portMocks)).doesNotThrowAnyException();
    }

    @Test
    void mockitoExtension_WhenSpringExtensionAlsoExtendsTheClass_FailsNamingIt() {
        JavaClasses springExtended = FIXTURES.that(belongToAnyOf(SpringExtensionMockFixture.class));

        assertThatThrownBy(() -> MockPortRule.MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION.check(springExtended))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(SpringExtensionMockFixture.class.getName()
                        + " is also extended with SpringExtension");
    }

    @Test
    void mockitoExtension_WhenMocksAreOpenedByHand_FailsNamingTheClass() {
        JavaClasses openedByHand = FIXTURES.that(belongToAnyOf(OpenMocksFixture.class));

        assertThatThrownBy(() -> MockPortRule.MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION.check(openedByHand))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(OpenMocksFixture.class.getName()
                        + " declares @Mock or @Spy fields or parameters without MockitoExtension")
                .hasMessageContaining(OpenMocksFixture.class.getName()
                        + " opens its mocks through MockitoAnnotations");
    }

    @Test
    void mockitoExtension_WhenOnlyAParameterIsMockedWithoutTheExtension_FailsNamingTheClass() {
        JavaClasses parameterMocks = FIXTURES.that(belongToAnyOf(ParameterMockFixture.class));

        assertThatThrownBy(() -> MockPortRule.MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION.check(parameterMocks))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ParameterMockFixture.class.getName()
                        + " declares @Mock or @Spy fields or parameters without MockitoExtension");
    }

    @Test
    void mockitoExtension_WhenTheClassItsEnclosingClassOrAMetaAnnotationExtendsIt_Passes() {
        JavaClasses extended = FIXTURES.that(belongToAnyOf(PortMockFixture.class, SettingsMockFixture.class));

        assertThatCode(() -> MockPortRule.MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION.check(extended))
                .doesNotThrowAnyException();
    }

    @Test
    void mocksOnlyAtPorts_WhenTheTestTreeIsChecked_Passes() {
        MockPortRule.MOCKS_ONLY_AT_PORTS.check(ImportedClasses.everything());
    }

    @Test
    void mockitoExtension_WhenTheTestTreeIsChecked_Passes() {
        MockPortRule.MOCKS_ONLY_THROUGH_MOCKITO_EXTENSION.check(ImportedClasses.everything());
    }
}
