package org.danteplanner.ratchetfixture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatchetFixtureRulesRuleTest {

    private static final JavaClasses HOLDER = new ClassFileImporter().importClasses(RatchetFixtureRules.class);

    @Test
    void failedDirectly_WhenCheckedAgainstTheHolder_Fails() {
        assertThatThrownBy(() -> RatchetFixtureRules.FAILED_DIRECTLY.check(HOLDER))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void failedThroughAHelper_WhenCheckedAgainstTheHolder_Fails() {
        assertFails(RatchetFixtureRules.FAILED_THROUGH_A_HELPER);
    }

    @Test
    void passedOnly_WhenCheckedAgainstTheHolder_Passes() {
        assertThatCode(() -> RatchetFixtureRules.PASSED_ONLY.check(HOLDER)).doesNotThrowAnyException();
    }

    private static void assertFails(ArchRule rule) {
        assertThatThrownBy(() -> rule.check(HOLDER)).isInstanceOf(AssertionError.class);
    }
}
