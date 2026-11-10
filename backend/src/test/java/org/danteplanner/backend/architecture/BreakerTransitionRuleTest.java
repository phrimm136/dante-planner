package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.breakertransitionfixture.BreakerFlippingFixture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BreakerTransitionRuleTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("org.danteplanner.breakertransitionfixture");

    @Test
    void onlyProbesMoveBreakers_WhenAnotherClassOpensAndClosesABreaker_FailsNamingBothCalls() {
        String fixture = BreakerFlippingFixture.class.getName();

        assertThatThrownBy(() -> BreakerTransitionRule.ONLY_PROBES_MOVE_BREAKERS.check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + ".open(")
                .hasMessageContaining("transitionToOpenState")
                .hasMessageContaining(fixture + ".close(")
                .hasMessageContaining("transitionToClosedState");
    }

    @Test
    void onlyProbesMoveBreakers_WhenProductionIsChecked_Passes() {
        BreakerTransitionRule.ONLY_PROBES_MOVE_BREAKERS.check(ImportedClasses.production());
    }
}
