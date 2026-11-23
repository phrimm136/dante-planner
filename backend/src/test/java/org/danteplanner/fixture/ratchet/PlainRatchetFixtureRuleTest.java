package org.danteplanner.fixture.ratchet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.backend.architecture.ImportedClasses;

import static org.assertj.core.api.Assertions.assertThat;

class PlainRatchetFixtureRuleTest {

    private static final JavaClasses PRODUCTION = ImportedClasses.production();

    private static final Set<String> EXEMPT = Set.of("Planner");

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
        assertThat(namedLikeTheFixture(PRODUCTION)).isEmpty();
    }

    @Test
    void fixturedRule_WhenRunOverItsFixture_NamesIt() {
        assertThat(namedLikeTheFixture(new ClassFileImporter().importClasses(RatchetFixtureRules.class)))
                .containsExactly("RatchetFixtureRules");
    }

    @Test
    void sharedHelperRule_WhenRunOverProduction_Passes() {
        assertThat(namesIn(PRODUCTION, "NoTypeHasThisName")).isEmpty();
    }

    @Test
    void passingOnlyRule_WhenRunOverProduction_Passes() {
        assertThat(namedLikeNothing(PRODUCTION)).isEmpty();
    }

    @Test
    void passingOnlyRule_WhenRunOverItsFixture_Passes() {
        assertThat(namedLikeNothing(new ClassFileImporter().importClasses(RatchetFixtureRules.class))).isEmpty();
    }

    @Test
    void ruleCoveredByTheList_WhenRunOverProduction_Passes() {
        Set<String> offenders = new TreeSet<>(namesIn(PRODUCTION, "Planner"));
        offenders.removeAll(EXEMPT);

        assertThat(offenders).isEmpty();
    }

    @Test
    void ruleCheckingAnEntry_WhenRunOverProduction_Passes() {
        Set<String> offenders = new TreeSet<>(namesIn(PRODUCTION, "Planner"));
        offenders.remove("NoTypeHasThisName");
        offenders.removeIf(EXEMPT::contains);

        assertThat(offenders).isEmpty();
    }

    @Test
    void listedRule_WhenRunOverProduction_Passes() {
        assertThat(EXEMPT).containsAll(namesIn(PRODUCTION, "Planner"));
    }

    @Test
    void exemptEntry_WhenStruckOffACopy_IsStillLive() {
        Set<String> stale = new HashSet<>(EXEMPT);
        PRODUCTION.stream().forEach(type -> stale.remove(type.getSimpleName()));

        assertThat(stale).isEmpty();
    }

    @Test
    void exemptEntry_WhenExpectedInTheScan_IsStillLive() {
        assertThat(namesIn(PRODUCTION, "Planner")).containsAll(EXEMPT);
    }

    private static List<String> namedLikeTheFixture(JavaClasses classes) {
        return namesIn(classes, "RatchetFixtureRules");
    }

    private static List<String> namedLikeNothing(JavaClasses classes) {
        return namesIn(classes, "NoTypeHasThisName");
    }

    private static List<String> namesIn(JavaClasses classes, String name) {
        return classes.stream().map(type -> type.getSimpleName()).filter(name::equals).toList();
    }
}
