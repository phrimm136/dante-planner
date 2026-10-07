package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.assertionsfixture.AssertjFixture;
import org.danteplanner.assertionsfixture.HamcrestAssertFixture;
import org.danteplanner.assertionsfixture.JunitAssertionsFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JunitAssertionsRuleTest {

    @Test
    void junitAssertion_WhenChecked_FailsNamingTheCaller() {
        JavaClasses junit = new ClassFileImporter().importClasses(JunitAssertionsFixture.class);

        assertThatThrownBy(() -> JunitAssertionsRule.ASSERTJ_ONLY.check(junit))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(JunitAssertionsFixture.class.getName()
                        + ".check(int)> calls method <org.junit.jupiter.api.Assertions.assertEquals(");
    }

    @Test
    void hamcrestAssertion_WhenChecked_FailsNamingTheCaller() {
        JavaClasses hamcrest = new ClassFileImporter().importClasses(HamcrestAssertFixture.class);

        assertThatThrownBy(() -> JunitAssertionsRule.ASSERTJ_ONLY.check(hamcrest))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(HamcrestAssertFixture.class.getName()
                        + ".check(int)> calls method <org.hamcrest.MatcherAssert.assertThat(");
    }

    @Test
    void assertjAssertion_WhenChecked_Passes() {
        JavaClasses assertj = new ClassFileImporter().importClasses(AssertjFixture.class);

        assertThatCode(() -> JunitAssertionsRule.ASSERTJ_ONLY.check(assertj)).doesNotThrowAnyException();
    }
}
