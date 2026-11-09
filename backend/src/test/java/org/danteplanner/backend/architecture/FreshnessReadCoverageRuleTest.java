package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.backend.shared.freshness.FreshnessReadGuard;
import org.danteplanner.freshnessfixture.BlankReasonFixture;
import org.danteplanner.freshnessfixture.ClassLevelDeclaredFixture;
import org.danteplanner.freshnessfixture.ClassLevelReadOnlyFixture;
import org.danteplanner.freshnessfixture.ClassLevelScopedReadFixture;
import org.danteplanner.freshnessfixture.DeclaredReadsFixture;
import org.danteplanner.freshnessfixture.DoublyDeclaredReadFixture;
import org.danteplanner.freshnessfixture.InterfaceScopedRead;
import org.danteplanner.freshnessfixture.InterfaceScopedReadFixture;
import org.danteplanner.freshnessfixture.PrivateHelperFixture;
import org.danteplanner.freshnessfixture.UnannotatedInterfaceScopedRead;
import org.danteplanner.freshnessfixture.UnannotatedInterfaceScopedReadFixture;
import org.danteplanner.freshnessfixture.UndeclaredReadFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreshnessReadCoverageRuleTest {

    @Test
    void undeclaredRead_WhenCoverageRuleRuns_FailsNamingTheMethod() {
        JavaClasses undeclared = new ClassFileImporter()
                .importClasses(UndeclaredReadFixture.class, ClassLevelReadOnlyFixture.class);

        assertThatThrownBy(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(undeclared))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(UndeclaredReadFixture.class.getName() + ".undeclared() declares no freshness read")
                        .contains(ClassLevelReadOnlyFixture.class.getName()
                                + ".inherited() declares no freshness read"));
    }

    @Test
    void scopedReadWithoutAMethodLevelReadOnly_WhenCoverageRuleRuns_FailsNamingTheMethod() {
        JavaClasses scoped = new ClassFileImporter().importClasses(
                ClassLevelScopedReadFixture.class, InterfaceScopedRead.class, InterfaceScopedReadFixture.class);

        assertThatThrownBy(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(scoped))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(ClassLevelScopedReadFixture.class.getName() + ".scopedByTheClass() returns a"
                                + " ScopedRead without @Transactional(readOnly = true) on the method")
                        .contains(InterfaceScopedReadFixture.class.getName() + ".scopedByTheInterface() returns a"
                                + " ScopedRead without @Transactional(readOnly = true) on the method")
                        .contains("put the annotation on the method")
                        .doesNotContain(InterfaceScopedRead.class.getName() + ".scopedByTheInterface()"));
    }

    @Test
    void doublyDeclaredRead_WhenCoverageRuleRuns_FailsNamingBothDeclarations() {
        JavaClasses doubly = new ClassFileImporter().importClasses(DoublyDeclaredReadFixture.class);

        assertThatThrownBy(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(doubly))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(DoublyDeclaredReadFixture.class.getName()
                                + ".scopedAndExempt() declares [a ScopedRead return, @FreshnessExempt]")
                        .contains(DoublyDeclaredReadFixture.class.getName()
                                + ".innerAndPending() declares [propagation = MANDATORY, @FreshnessPending]"));
    }

    @Test
    void blankReason_WhenCoverageRuleRuns_FailsNamingTheMethod() {
        JavaClasses blank = new ClassFileImporter().importClasses(BlankReasonFixture.class);

        assertThatThrownBy(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(blank))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(BlankReasonFixture.class.getName() + ".blankExempt() names a blank freshness reason")
                        .contains(BlankReasonFixture.class.getName()
                                + ".blankPending() names a blank freshness reason"));
    }

    @Test
    void declaredReads_WhenCoverageRuleRuns_Pass() {
        JavaClasses declared = new ClassFileImporter()
                .importClasses(DeclaredReadsFixture.class, ClassLevelDeclaredFixture.class);

        assertThatCode(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(declared))
                .doesNotThrowAnyException();
    }

    @Test
    void unadvisableMethods_WhenCoverageRuleRuns_AreNotChecked() {
        JavaClasses unadvisable = new ClassFileImporter().importClasses(PrivateHelperFixture.class,
                UnannotatedInterfaceScopedRead.class, UnannotatedInterfaceScopedReadFixture.class);

        assertThatCode(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(unadvisable))
                .doesNotThrowAnyException();
    }

    @Test
    void freshnessPlatform_WhenCoverageRuleRuns_IsNotChecked() {
        JavaClasses platform = new ClassFileImporter()
                .importClasses(FreshnessReadGuard.class, FreshnessReadGuard.Attempt.class);

        assertThatCode(() -> FreshnessReadCoverageRule.READ_ONLY_METHODS_DECLARE_EXACTLY_ONE_FRESHNESS_READ
                .check(platform))
                .doesNotThrowAnyException();
    }
}
