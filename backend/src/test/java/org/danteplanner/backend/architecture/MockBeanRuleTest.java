package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.mockbeanfixture.MockitoBeanFieldFixture;
import org.danteplanner.mockbeanfixture.TestBeanFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockBeanRuleTest {

    @Test
    void mockOverrideOnFieldsAndTheType_WhenChecked_FailsNamingEach() {
        JavaClasses mocked = new ClassFileImporter().importClasses(MockitoBeanFieldFixture.class);

        assertThatThrownBy(() -> MockBeanRule.NO_MOCKITO_BEAN_OVERRIDES.check(mocked))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(MockitoBeanFieldFixture.class.getName()
                        + ".spied overrides a bean with a Mockito mock")
                .hasMessageContaining(MockitoBeanFieldFixture.class.getName()
                        + " overrides a bean with a Mockito mock")
                .hasMessageContaining(MockitoBeanFieldFixture.class.getName()
                        + ".bootMocked overrides a bean with a Mockito mock")
                .hasMessageContaining(MockitoBeanFieldFixture.class.getName()
                        + ".metaMocked overrides a bean with a Mockito mock");
    }

    @Test
    void testBeanOverride_WhenChecked_Passes() {
        JavaClasses testBean = new ClassFileImporter().importClasses(TestBeanFixture.class);

        assertThatCode(() -> MockBeanRule.NO_MOCKITO_BEAN_OVERRIDES.check(testBean))
                .doesNotThrowAnyException();
    }
}
