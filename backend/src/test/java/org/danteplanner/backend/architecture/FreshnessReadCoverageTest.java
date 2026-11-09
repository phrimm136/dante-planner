package org.danteplanner.backend.architecture;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.shared.freshness.FreshnessPending;
import org.danteplanner.freshnessfixture.DeclaredReadsFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FreshnessReadCoverageTest {

    private static final int PENDING_PIN = 12;

    @ArchTest
    static final ArchRule read_only_methods_declare_exactly_one_freshness_read =
            FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ;

    @Test
    void pendingReads_WhenProductionIsScanned_CountExactlyThePin() {
        assertPinned(ImportedClasses.production(), PENDING_PIN);
    }

    @Test
    void pendingReads_WhenFixtureIsScanned_CountOnlyTheFreshnessPendingMethods() {
        assertThat(pendingReads(fixture()))
                .containsExactly(DeclaredReadsFixture.class.getName() + ".pending()");
    }

    @Test
    void pendingPin_WhenTheCountRisesAboveIt_FailsAsANewPendingRead() {
        assertThatThrownBy(() -> assertPinned(fixture(), 0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("new @FreshnessPending read")
                .hasMessageContaining(DeclaredReadsFixture.class.getName() + ".pending()");
    }

    @Test
    void pendingPin_WhenTheCountFallsBelowIt_FailsAskingToLowerThePin() {
        assertThatThrownBy(() -> assertPinned(fixture(), 2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("lower PENDING_PIN to 1");
    }

    @Test
    void pendingPin_WhenTheCountEqualsIt_Passes() {
        assertThatCode(() -> assertPinned(fixture(), 1)).doesNotThrowAnyException();
    }

    private static void assertPinned(JavaClasses classes, int pin) {
        List<String> pending = pendingReads(classes);
        assertThat(pending.size())
                .as("a new @FreshnessPending read among %s: the pending count may only go down; "
                        + "declare a ScopedRead, propagation = MANDATORY or @FreshnessExempt instead", pending)
                .isLessThanOrEqualTo(pin);
        assertThat(pending.size())
                .as("the @FreshnessPending count fell below %d: lower PENDING_PIN to %d; the pending count "
                        + "may only go down, and the pin is lowered when M2 replaces a pending read", pin,
                        pending.size())
                .isGreaterThanOrEqualTo(pin);
    }

    private static List<String> pendingReads(JavaClasses classes) {
        return classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(FreshnessPending.class))
                .map(JavaMethod::getFullName)
                .sorted()
                .toList();
    }

    private static JavaClasses fixture() {
        return new ClassFileImporter().importClasses(DeclaredReadsFixture.class);
    }
}
