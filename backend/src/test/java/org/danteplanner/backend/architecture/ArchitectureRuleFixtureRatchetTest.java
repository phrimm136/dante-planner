package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRuleFixtureRatchetTest {

    private static final String ARCHITECTURE = "org.danteplanner.backend.architecture";
    private static final String RATCHET_FIXTURE = "org.danteplanner.ratchetfixture";
    private static final String FROZEN_LIST = "backend/config/architecture-rules-without-fixtures.txt";
    private static final Path FROZEN_LIST_PATH = Path.of("config/architecture-rules-without-fixtures.txt");
    private static final String TESTABLE = "org.junit.platform.commons.annotation.Testable";
    private static final Set<String> FAILURE_ASSERTIONS = Set.of(
            "org.assertj.core.api.Assertions.assertThatThrownBy",
            "org.assertj.core.api.Assertions.assertThatExceptionOfType",
            "org.junit.jupiter.api.Assertions.assertThrows");

    private static final JavaClasses ARCHITECTURE_CLASSES = new ClassFileImporter().importPackages(ARCHITECTURE);
    private static final Rules RULES = rulesIn(ARCHITECTURE_CLASSES.stream()
            .filter(holder -> !isFixturePackage(holder.getPackageName()))
            .toList());
    private static final SortedSet<String> FIXTURED = rulesWithNegativeFixture(ARCHITECTURE_CLASSES, RULES);

    @Test
    void unlistedRule_WhenItHasNoNegativeFixture_IsNamed() {
        Set<String> missing = new TreeSet<>(RULES.ids());
        missing.removeAll(FIXTURED);
        missing.removeAll(frozen());

        assertThat(RULES.ids()).as("no rule found under %s; the scan itself is broken", ARCHITECTURE).isNotEmpty();
        assertThat(missing)
                .as("these rules are never run against a violating fixture by a test asserting that they fail; "
                        + "add a *RuleTest that does (PrivilegedRouteCoverageRuleTest is the shape). "
                        + "%s only shrinks", FROZEN_LIST)
                .isEmpty();
    }

    @Test
    void listedRule_WhenItGainsANegativeFixture_MustLeaveTheList() {
        Set<String> fixturedButListed = new TreeSet<>(frozen());
        fixturedButListed.retainAll(FIXTURED);

        assertThat(fixturedButListed)
                .as("these rules now have a negative fixture; delete their lines from %s", FROZEN_LIST)
                .isEmpty();
    }

    @Test
    void listedRule_WhenItNoLongerExists_MustLeaveTheList() {
        Set<String> gone = new TreeSet<>(frozen());
        gone.removeAll(RULES.ids());

        assertThat(gone)
                .as("these rules no longer exist under %s; delete their lines from %s", ARCHITECTURE, FROZEN_LIST)
                .isEmpty();
    }

    @Test
    void frozenList_WhenRead_IsSortedWithoutDuplicates() {
        assertThat(frozenLines())
                .as("%s holds one Class.member per line, sorted", FROZEN_LIST)
                .isSortedAccordingTo(Comparator.naturalOrder())
                .doesNotHaveDuplicates();
    }

    @Test
    void ruleScan_WhenRunOverTheRatchetFixture_CountsEachRuleOnceAndSkipsTheAlias() {
        Rules fixtureRules = rulesIn(new ClassFileImporter().importPackages(RATCHET_FIXTURE));

        assertThat(fixtureRules.ids()).containsExactly(
                "RatchetFixtureRules.FAILED_DIRECTLY",
                "RatchetFixtureRules.FAILED_THROUGH_A_HELPER",
                "RatchetFixtureRules.NEVER_CHECKED",
                "RatchetFixtureRules.PASSED_ONLY");
    }

    @Test
    void fixtureScan_WhenRunOverTheRatchetFixture_CreditsOnlyRulesAssertedToFail() {
        JavaClasses fixture = new ClassFileImporter().importPackages(RATCHET_FIXTURE);

        assertThat(rulesWithNegativeFixture(fixture, rulesIn(fixture))).containsExactly(
                "RatchetFixtureRules.FAILED_DIRECTLY",
                "RatchetFixtureRules.FAILED_THROUGH_A_HELPER");
    }

    private static Set<String> frozen() {
        return new TreeSet<>(frozenLines());
    }

    private static List<String> frozenLines() {
        try {
            return Files.readAllLines(FROZEN_LIST_PATH).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Rules rulesIn(Iterable<JavaClass> holders) {
        Set<JavaField> ruleFields = new LinkedHashSet<>();
        Map<String, String> idByMember = new HashMap<>();
        for (JavaClass holder : holders) {
            holder.getFields().stream()
                    .filter(ArchitectureRuleFixtureRatchetTest::isRuleField)
                    .forEach(ruleFields::add);
            holder.getMethods().stream()
                    .filter(method -> method.isAnnotatedWith(ArchTest.class))
                    .forEach(method -> idByMember.put(memberKey(holder, method.getName()), idOf(holder, method.getName())));
        }
        for (JavaField field : ruleFields) {
            JavaField canonical = canonicalOf(field, ruleFields);
            idByMember.put(memberKey(field.getOwner(), field.getName()), idOf(canonical.getOwner(), canonical.getName()));
        }
        return new Rules(new TreeSet<>(idByMember.values()), idByMember);
    }

    private static boolean isRuleField(JavaField field) {
        return field.getRawType().isAssignableTo(ArchRule.class) || field.isAnnotatedWith(ArchTest.class);
    }

    private static JavaField canonicalOf(JavaField field, Set<JavaField> ruleFields) {
        JavaField current = field;
        Set<JavaField> visited = new LinkedHashSet<>();
        while (visited.add(current)) {
            Optional<JavaField> aliased = aliasedBy(current, ruleFields);
            if (aliased.isEmpty()) {
                return current;
            }
            current = aliased.get();
        }
        return current;
    }

    private static Optional<JavaField> aliasedBy(JavaField field, Set<JavaField> ruleFields) {
        if (!field.getModifiers().contains(JavaModifier.STATIC)) {
            return Optional.empty();
        }
        Set<String> readDuringInitialization = field.getOwner().getStaticInitializer()
                .map(initializer -> initializer.getFieldAccesses().stream()
                        .filter(access -> access.getAccessType() == AccessType.GET)
                        .map(access -> memberKey(access.getTargetOwner(), access.getName()))
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        return ruleFields.stream()
                .filter(other -> !other.equals(field))
                .filter(other -> readDuringInitialization.contains(memberKey(other.getOwner(), other.getName())))
                .filter(other -> valueOf(other) == valueOf(field))
                .findFirst();
    }

    private static Object valueOf(JavaField field) {
        Field reflected = field.reflect();
        if (!Modifier.isStatic(reflected.getModifiers())) {
            return null;
        }
        try {
            reflected.setAccessible(true);
            return reflected.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read " + field.getFullName(), e);
        }
    }

    private static SortedSet<String> rulesWithNegativeFixture(JavaClasses classes, Rules rules) {
        SortedSet<String> fixtured = new TreeSet<>();
        for (JavaClass candidate : classes) {
            if (!isFixtureTest(candidate)) {
                continue;
            }
            for (JavaMethod test : candidate.getMethods()) {
                if (!test.isMetaAnnotatedWith(TESTABLE)) {
                    continue;
                }
                Set<JavaCodeUnit> reached = sameClassClosure(test);
                if (reached.stream().anyMatch(ArchitectureRuleFixtureRatchetTest::assertsAFailure)) {
                    reached.forEach(unit -> fixtured.addAll(rulesReferencedBy(unit, rules)));
                }
            }
        }
        return fixtured;
    }

    private static boolean isFixtureTest(JavaClass candidate) {
        return candidate.getSimpleName().endsWith("RuleTest")
                || candidate.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> isFixturePackage(dependency.getTargetClass().getPackageName()));
    }

    private static boolean isFixturePackage(String packageName) {
        return packageName.startsWith("org.danteplanner.")
                && Arrays.stream(packageName.split("\\.")).anyMatch(segment -> segment.endsWith("fixture"));
    }

    private static Set<JavaCodeUnit> sameClassClosure(JavaMethod test) {
        Set<JavaCodeUnit> reached = new LinkedHashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.add(test);
        while (!pending.isEmpty()) {
            JavaCodeUnit unit = pending.remove();
            if (!reached.add(unit)) {
                continue;
            }
            unit.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getTargetOwner().equals(test.getOwner()))
                    .map(call -> call.getTarget())
                    .map(MethodCallTarget::resolveMember)
                    .flatMap(Optional::stream)
                    .forEach(pending::add);
        }
        return reached;
    }

    private static boolean assertsAFailure(JavaCodeUnit unit) {
        return unit.getMethodCallsFromSelf().stream()
                .anyMatch(call -> FAILURE_ASSERTIONS.contains(memberKey(call.getTargetOwner(), call.getName())));
    }

    private static Set<String> rulesReferencedBy(JavaCodeUnit unit, Rules rules) {
        Set<String> referenced = new TreeSet<>();
        unit.getFieldAccesses().stream()
                .filter(access -> access.getAccessType() == AccessType.GET)
                .map(access -> rules.idByMember().get(memberKey(access.getTargetOwner(), access.getName())))
                .filter(id -> id != null)
                .forEach(referenced::add);
        unit.getMethodCallsFromSelf().stream()
                .map(call -> rules.idByMember().get(memberKey(call.getTargetOwner(), call.getName())))
                .filter(id -> id != null)
                .forEach(referenced::add);
        return referenced;
    }

    private static String memberKey(JavaClass owner, String member) {
        return owner.getName() + "." + member;
    }

    private static String idOf(JavaClass owner, String member) {
        return owner.getSimpleName() + "." + member;
    }

    private record Rules(SortedSet<String> ids, Map<String, String> idByMember) {
    }
}
