package org.danteplanner.backend.architecture;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.core.BridgeMethodResolver;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoidCheckRuleTest {

    private static final String FIXTURE = "org.danteplanner.fixture.decisionpurity.ParseConventionFixtures";
    private static final List<String> LISTED = ShellBranches.lines(Path.of("config/void-checks.txt"));
    private static final Pattern CHECK_NAME = Pattern.compile("(require|check|validate|ensure|verify).*");
    private static final DescribedPredicate<JavaMethod> VOID_CHECKS = describe("are void checks", method ->
            CHECK_NAME.matcher(method.getName()).matches()
                    && method.getRawReturnType().isEquivalentTo(void.class)
                    && isHandWritten(method)
                    && overriddenBy(method).findAny().isEmpty());

    @Test
    void production_WhenScanned_HoldsExactlyTheListedVoidChecks() {
        assertThatCode(() -> voidChecksAreExactly(LISTED).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void list_WhenRead_IsSortedWithoutDuplicates() {
        assertThat(LISTED).isSortedAccordingTo(Comparator.naturalOrder()).doesNotHaveDuplicates();
    }

    @Test
    void fixture_WhenAVoidCheckIsNotListed_FailsNamingEachSiteOnce() {
        assertThatThrownBy(() -> voidChecksAreExactly(List.of()).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unlisted " + FIXTURE + "$Checks.requireDeclared(java.lang.String)")
                .hasMessageContaining("unlisted " + FIXTURE + "$VoidChecks.requirePresent(java.lang.String)")
                .hasMessageContaining("unlisted " + FIXTURE + "$VoidChecks.checkRange(int)")
                .hasMessageContaining("unlisted " + FIXTURE + "$VoidChecks.validateName(java.lang.String)")
                .hasMessageContaining("unlisted " + FIXTURE + "$VoidChecks.ensureOpen()")
                .hasMessageContaining("unlisted " + FIXTURE + "$VoidChecks.verifyOwner()")
                .hasMessageContaining("(6 times)");
    }

    @Test
    void fixture_WhenAListedSiteIsGone_FailsNamingTheLine() {
        assertThatThrownBy(() -> voidChecksAreExactly(List.of("gone.Type.requireGone()")).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete gone.Type.requireGone()");
    }

    static ArchCondition<JavaMethod> beExactly(List<String> listed) {
        return new ArchCondition<>("be exactly the listed ones") {

            private final Set<String> found = new LinkedHashSet<>();

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String site = method.getFullName();
                found.add(site);
                if (!listed.contains(site)) {
                    events.add(SimpleConditionEvent.violated(method, "unlisted " + site));
                }
            }

            @Override
            public void finish(ConditionEvents events) {
                listed.stream()
                        .filter(line -> !found.contains(line))
                        .forEach(line -> events.add(SimpleConditionEvent.violated(line, "delete " + line)));
            }
        };
    }

    static boolean isHandWritten(JavaMethod method) {
        return !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                && !method.getModifiers().contains(JavaModifier.BRIDGE)
                && !method.isAnnotatedWith("lombok.Generated");
    }

    static Stream<JavaMethod> overriddenBy(JavaMethod method) {
        if (!isInherited(method)) {
            return Stream.empty();
        }
        List<JavaMethod> signatures = Stream.concat(Stream.of(method), bridgesTo(method)).toList();
        return Stream.concat(
                        method.getOwner().getAllRawSuperclasses().stream(),
                        method.getOwner().getAllRawInterfaces().stream())
                .flatMap(supertype -> supertype.getMethods().stream())
                .filter(VoidCheckRuleTest::isInherited)
                .filter(inherited -> signatures.stream().anyMatch(signature -> sameSignature(signature, inherited)));
    }

    static JavaClasses fixtureClasses() {
        return new ClassFileImporter().importPackages("org.danteplanner.fixture.decisionpurity")
                .that(nameStartingWith(FIXTURE));
    }

    private static ArchRule voidChecksAreExactly(List<String> listed) {
        return methods()
                .that(VOID_CHECKS)
                .should(beExactly(listed))
                .allowEmptyShould(true)
                .as("a check returns a @Parsed type that carries its fact; the void checks left are exactly"
                        + " the lines of backend/config/void-checks.txt, which only shrinks: an unlisted check is changed to"
                        + " return its fact, not added");
    }

    private static Stream<JavaMethod> bridgesTo(JavaMethod method) {
        return method.getOwner().getMethods().stream()
                .filter(bridge -> bridge.getModifiers().contains(JavaModifier.BRIDGE))
                .filter(bridge -> bridge.getName().equals(method.getName()))
                .filter(bridge -> BridgeMethodResolver.findBridgedMethod(bridge.reflect()).equals(method.reflect()));
    }

    private static boolean isInherited(JavaMethod method) {
        return !method.getModifiers().contains(JavaModifier.STATIC)
                && !method.getModifiers().contains(JavaModifier.PRIVATE);
    }

    private static boolean sameSignature(JavaMethod method, JavaMethod other) {
        return method.getName().equals(other.getName())
                && method.getRawParameterTypes().equals(other.getRawParameterTypes());
    }
}
