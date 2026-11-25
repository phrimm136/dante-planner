package org.danteplanner.backend.architecture;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagParameterRuleTest {

    private static final String FIXTURE = "org.danteplanner.fixture.decisionpurity.ParseConventionFixtures";
    private static final List<String> LISTED = ShellBranches.lines(Path.of("config/flag-parameters.txt"));
    private static final DescribedPredicate<JavaMethod> FLAG_METHODS = describe("take a flag", method ->
            method.getModifiers().contains(JavaModifier.PUBLIC)
                    && takesABoolean(method)
                    && !isSetter(method)
                    && VoidCheckRuleTest.isHandWritten(method)
                    && VoidCheckRuleTest.overriddenBy(method).noneMatch(FlagParameterRuleTest::answersForItsOverrides));

    @Test
    void production_WhenScanned_HoldsExactlyTheListedFlagParameters() {
        assertThatCode(() -> flagParametersAreExactly(LISTED).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void list_WhenRead_IsSortedWithoutDuplicates() {
        assertThat(LISTED).isSortedAccordingTo(Comparator.naturalOrder()).doesNotHaveDuplicates();
    }

    @Test
    void fixture_WhenAFlagParameterIsNotListed_FailsNamingEachSiteOnce() {
        assertThatThrownBy(() -> flagParametersAreExactly(List.of()).check(VoidCheckRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("add " + FIXTURE + "$Flagged.declaredFlag(boolean)")
                .hasMessageContaining("add " + FIXTURE + "$PublicFlags.widenedFlag(boolean)")
                .hasMessageContaining("add " + FIXTURE + "$PublicFlags.primitiveFlag(java.lang.String, boolean)")
                .hasMessageContaining("add " + FIXTURE + "$PublicFlags.boxedFlag(java.lang.Boolean)")
                .hasMessageContaining("add " + FIXTURE + "$PublicFlags.setTwo(boolean, java.lang.String)")
                .hasMessageContaining("add " + FIXTURE + "$PublicFlags.settle(boolean)")
                .hasMessageContaining(
                        "add " + FIXTURE + "$LibraryConsumer.accept(java.lang.Integer, java.lang.Boolean)")
                .hasMessageContaining("(7 times)");
    }

    @Test
    void fixture_WhenAListedSiteIsGone_FailsNamingTheLine() {
        assertThatThrownBy(() -> flagParametersAreExactly(List.of("gone.Type.flag(boolean)"))
                .check(VoidCheckRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete gone.Type.flag(boolean)");
    }

    private static ArchRule flagParametersAreExactly(List<String> listed) {
        return methods()
                .that(FLAG_METHODS)
                .should(VoidCheckRuleTest.beExactly(listed))
                .allowEmptyShould(true)
                .as("a public method takes an enum, or is two methods, instead of a boolean; the flag parameters left"
                        + " are exactly the lines of backend/config/flag-parameters.txt, which only shrinks");
    }

    private static boolean takesABoolean(JavaMethod method) {
        return method.getRawParameterTypes().stream()
                .anyMatch(type -> type.isEquivalentTo(boolean.class) || type.isEquivalentTo(Boolean.class));
    }

    private static boolean isSetter(JavaMethod method) {
        return method.getName().matches("set[A-Z].*") && method.getRawParameterTypes().size() == 1;
    }

    private static boolean answersForItsOverrides(JavaMethod overridden) {
        return overridden.getModifiers().contains(JavaModifier.PUBLIC)
                || !overridden.getOwner().getPackageName().startsWith("org.danteplanner.");
    }
}
