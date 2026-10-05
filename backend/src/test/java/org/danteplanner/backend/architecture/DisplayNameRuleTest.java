package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.displaynamefixture.DisplayNamedFixture;
import org.danteplanner.displaynamefixture.UnnamedFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNameRuleTest {

    @Test
    void displayNamedClassAndMethod_WhenChecked_FailNamingBoth() {
        JavaClasses named = new ClassFileImporter().importClasses(DisplayNamedFixture.class);

        assertThatThrownBy(() -> DisplayNameRule.TESTS_CARRY_NO_DISPLAY_NAME.check(named))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(DisplayNamedFixture.class.getName() + " carries a DisplayName annotation")
                .hasMessageContaining(DisplayNamedFixture.class.getName()
                        + ".subject_WhenNamedTwice_CarriesADisplayName() carries a DisplayName annotation");
    }

    @Test
    void unnamedClassAndNestedGroup_WhenChecked_Pass() {
        JavaClasses unnamed = new ClassFileImporter().importClasses(UnnamedFixture.class, UnnamedFixture.Group.class);

        assertThatCode(() -> DisplayNameRule.TESTS_CARRY_NO_DISPLAY_NAME.check(unnamed))
                .doesNotThrowAnyException();
    }
}
