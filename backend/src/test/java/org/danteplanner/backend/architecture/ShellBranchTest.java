package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.AbstractClassesTransformer;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ClassesTransformer;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;
import com.tngtech.archunit.library.freeze.ViolationStore;

import org.danteplanner.fixture.shellbranch.ShellBranchFixtures;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.all;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShellBranchTest {

    static final ArchRule EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH =
            all(methodsThatBranch())
                    .should(reachNoEffectButLogging())
                    .as("a method with a forward conditional jump other than a loop exit reaches no effect but"
                            + " logging: an effectful method may branch only by switching on a decision value"
                            + " (a sealed type or enum)");

    private static final Set<JavaModifier> NOT_ROOTS =
            Set.of(JavaModifier.SYNTHETIC, JavaModifier.BRIDGE, JavaModifier.ABSTRACT);

    private static final String FIXTURE = ShellBranchFixtures.class.getName();

    private static final Path STORE = Path.of("config/archunit_store");

    private static final String STORED_RULES = "stored.rules";

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("org.danteplanner.fixture.shellbranch");

    private static final JavaClasses SHELLS_AND_A_LATE_VIOLATION = FIXTURES;

    private static final JavaClasses SHELLS =
            FIXTURES.that(not(belongToAnyOf(ShellBranchFixtures.LateViolation.class)));

    @TempDir
    Path storeDirectory;

    @Test
    void rule_WhenRunOverTheShells_NamesOnlyTheEffectfulMethodsWithAForwardConditionalJump() {
        List<String> named = EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH.evaluate(SHELLS).getFailureReport().getDetails();

        assertThat(named).containsExactly(
                FIXTURE + ".actGuarded(" + FIXTURE + "$Verdict, boolean)",
                FIXTURE + ".choose(boolean, boolean, boolean)",
                FIXTURE + ".drainEitherWay(boolean)",
                FIXTURE + ".drainSkippingAfterA(boolean)",
                FIXTURE + ".drainWhenAllowed(boolean)",
                FIXTURE + ".f(boolean, boolean)",
                FIXTURE + ".route(java.lang.String)",
                FIXTURE + ".save(boolean)");
    }

    @Test
    void frozenRule_WhenANewViolationJoinsAFrozenStore_FailsNamingIt() {
        ViolationStore store = storeIn(storeDirectory);
        FreezingArchRule.freeze(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH).persistIn(store).check(SHELLS);

        assertThatThrownBy(() -> FreezingArchRule.freeze(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH)
                .persistIn(store)
                .check(SHELLS_AND_A_LATE_VIOLATION))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ShellBranchFixtures.LateViolation.class.getName() + ".late(boolean)")
                .hasMessageNotContaining(FIXTURE + ".save(boolean)");
    }

    @Test
    void frozenRule_WhenAStoredMethodNoLongerBranches_RemovesItsEntry() {
        ViolationStore store = storeIn(storeDirectory);
        FreezingArchRule.freeze(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH).persistIn(store).check(SHELLS);
        List<String> current = store.getViolations(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH);
        List<String> withAFixedSite = new ArrayList<>(current);
        withAFixedSite.add(FIXTURE + ".act(" + FIXTURE + "$Verdict)");
        store.save(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH, withAFixedSite);

        FreezingArchRule.freeze(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH).persistIn(store).check(SHELLS);

        assertThat(store.getViolations(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH)).isEqualTo(current);
    }

    @Test
    void frozenRule_WhenTheMainTreeIsChecked_Passes() {
        assertThatCode(() -> FreezingArchRule.freeze(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH)
                .check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void frozenStore_WhenRead_HoldsOnlyThisRuleUnderItsCurrentDescription() throws IOException {
        Properties storedRules = new Properties();
        try (Reader reader = Files.newBufferedReader(STORE.resolve(STORED_RULES))) {
            storedRules.load(reader);
        }
        Set<String> files;
        try (Stream<Path> listed = Files.list(STORE)) {
            files = listed.map(file -> file.getFileName().toString()).collect(Collectors.toSet());
        }

        assertThat(storedRules.stringPropertyNames())
                .as("%s/%s is keyed by the rule's description, so a reworded rule is stored as a new rule and"
                        + " its old violations stop excusing anything; refreeze once (freeze.refreeze=true with"
                        + " freeze.store.default.allowStoreUpdate=true) and delete the old entry", STORE, STORED_RULES)
                .containsExactly(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH.getDescription());
        assertThat(files)
                .as("%s holds %s and the violation files it names, nothing else", STORE, STORED_RULES)
                .containsExactlyInAnyOrder(STORED_RULES,
                        storedRules.getProperty(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH.getDescription()));
    }

    @Test
    void frozenViolations_WhenScanned_AreStillViolations() {
        Properties properties = new Properties();
        properties.setProperty("default.path", STORE.toString());
        properties.setProperty("default.allowStoreCreation", "false");
        properties.setProperty("default.allowStoreUpdate", "false");
        TextFileBasedViolationStore store = new TextFileBasedViolationStore();
        store.initialize(properties);
        Set<String> stale = new TreeSet<>(store.getViolations(EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH));
        EFFECTFUL_METHODS_BRANCH_ONLY_BY_SWITCH.evaluate(ImportedClasses.production())
                .getFailureReport().getDetails()
                .forEach(stale::remove);

        assertThat(stale)
                .as("these no longer branch or no longer reach an effect, so the entry now excuses a violation"
                        + " nobody commits; delete the lines from %s (or refreeze) and let the rule cover the"
                        + " method again", STORE)
                .isEmpty();
    }

    private static ViolationStore storeIn(Path directory) {
        Properties properties = new Properties();
        properties.setProperty("default.path", directory.toString());
        properties.setProperty("default.allowStoreCreation", "true");
        properties.setProperty("default.allowStoreUpdate", "true");
        TextFileBasedViolationStore store = new TextFileBasedViolationStore();
        store.initialize(properties);
        return new PinnedStore(store);
    }

    private record PinnedStore(ViolationStore delegate) implements ViolationStore {

        @Override
        public void initialize(Properties properties) {
        }

        @Override
        public boolean contains(ArchRule rule) {
            return delegate.contains(rule);
        }

        @Override
        public void save(ArchRule rule, List<String> violations) {
            delegate.save(rule, violations);
        }

        @Override
        public List<String> getViolations(ArchRule rule) {
            return delegate.getViolations(rule);
        }
    }

    private static ClassesTransformer<JavaMethod> methodsThatBranch() {
        return new AbstractClassesTransformer<>("methods with a forward conditional jump") {
            @Override
            public Iterable<JavaMethod> doTransform(JavaClasses classes) {
                return classes.stream()
                        .flatMap(ShellBranchTest::methodsThatBranchIn)
                        .sorted(Comparator.comparing(JavaMethod::getFullName))
                        .toList();
            }
        };
    }

    private static Stream<JavaMethod> methodsThatBranchIn(JavaClass clazz) {
        List<JavaMethod> roots = clazz.getMethods().stream()
                .filter(method -> method.getModifiers().stream().noneMatch(NOT_ROOTS::contains))
                .toList();
        if (roots.isEmpty()) {
            return Stream.empty();
        }
        Map<String, Integer> jumps = Branches.forwardConditionalJumps(clazz);
        return roots.stream().filter(method -> jumpsOf(method, jumps) > 0);
    }

    private static int jumpsOf(JavaMethod method, Map<String, Integer> jumps) {
        Integer count = jumps.get(Branches.key(method));
        if (count == null) {
            throw new IllegalStateException("no bytecode read for " + method.getFullName() + " as "
                    + Branches.key(method) + "; read " + jumps.keySet());
        }
        return count;
    }

    private static ArchCondition<JavaMethod> reachNoEffectButLogging() {
        return new ArchCondition<>("reach no effect but logging") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean effectful = Effects.reachableFrom(method).keySet().stream()
                        .anyMatch(effect -> !effect.equals("log"));
                if (effectful) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName()));
                }
            }
        };
    }
}
