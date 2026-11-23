package org.danteplanner.fixture.ratchet;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.backend.architecture.ImportedClasses;

import static org.assertj.core.api.Assertions.assertThat;

class PlainRatchetFixtureRuleTest {

    private static final JavaClasses PRODUCTION = ImportedClasses.production();

    @Test
    void unfixturedRule_WhenRunOverProduction_Passes() {
        assertThat(ImportedClasses.production().stream().noneMatch(type -> type.getSimpleName().equals("NoTypeHasThisName"))).isTrue();
    }

    @Test
    void heldRule_WhenRunOverProduction_Passes() {
        assertThat(PRODUCTION.stream().noneMatch(type -> type.getSimpleName().equals("NoTypeHasThisName"))).isTrue();
    }

    @Test
    void fixturedRule_WhenRunOverProduction_Passes() {
        assertThat(namedLikeTheFixture(PRODUCTION)).isFalse();
    }

    @Test
    void fixturedRule_WhenRunOverItsFixture_NamesIt() {
        assertThat(namedLikeTheFixture(new ClassFileImporter().importClasses(RatchetFixtureRules.class))).isTrue();
    }

    private static boolean namedLikeTheFixture(JavaClasses classes) {
        return classes.stream().anyMatch(type -> type.getSimpleName().equals("RatchetFixtureRules"));
    }
}
