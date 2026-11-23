package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.fixture.shellbranch.BranchShapes;
import org.danteplanner.fixture.shellbranch.ShellBranchFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShellBranchTest {

    private static final String FIXTURE_PACKAGE = "org.danteplanner.fixture.shellbranch";

    private static final Path FIXTURE_SOURCES = Path.of("src/test/java/org/danteplanner/fixture/shellbranch");

    private static final String SHELLS = ShellBranchFixtures.class.getName();

    private static final String SHAPES = BranchShapes.class.getName();

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);

    private static final SortedMap<String, Integer> COUNTED =
            ShellBranches.in(FIXTURES, FIXTURE_SOURCES, FIXTURE_PACKAGE);

    private static final String SAVE = SHELLS + ".save(boolean)";

    private static final String LATE = ShellBranchFixtures.LateViolation.class.getName() + ".late(boolean)";

    private static final String ACT = SHELLS + ".act(" + SHELLS + "$Verdict)";

    @TempDir
    Path directory;

    @Test
    void mainTree_WhenCounted_RaisesNoLineOfTheStore() {
        assertThat(ShellBranches.raised(ShellBranches.read(ShellBranches.STORE), mainBranches()))
                .as("an effectful method may branch only by switching on a decision value (a sealed type or enum);"
                        + " these gained branches")
                .isEmpty();
    }

    @Test
    void mainTree_WhenCounted_LeavesNoLineOfTheStoreStale() {
        assertThat(ShellBranches.stale(ShellBranches.read(ShellBranches.STORE), mainBranches()))
                .as("these lost branches, are gone, or no longer reach an effect; run `checks be freeze-branches`"
                        + " so that %s holds the new counts", ShellBranches.STORE)
                .isEmpty();
    }

    @Test
    void store_WhenRead_IsSorted() {
        assertThat(ShellBranches.lines(ShellBranches.STORE))
                .containsExactlyElementsOf(ShellBranches.lines(written(ShellBranches.read(ShellBranches.STORE))));
    }

    @Test
    void shells_WhenCounted_AreTheEffectfulMethodsWithABranch() {
        assertThat(countedIn(SHELLS)).containsExactlyInAnyOrderEntriesOf(Map.of(
                LATE, 1,
                ACT.replace(".act(", ".actGuarded(").replace(")", ", boolean)"), 1,
                SHELLS + ".choose(boolean, boolean, boolean)", 1,
                SHELLS + ".drainEitherWay(boolean)", 1,
                SHELLS + ".drainSkippingAfterA(boolean)", 1,
                SHELLS + ".drainWhenAllowed(boolean)", 1,
                SHELLS + ".f(boolean, boolean)", 1,
                SHELLS + ".route(java.lang.String)", 1,
                SAVE, 1));
    }

    @Test
    void shapes_WhenCounted_MatchOneSourceMethodEach() {
        assertThat(countedIn(SHAPES)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(SHAPES + ".<init>(" + SHELLS + "$Port, java.util.Queue, boolean)", 1),
                Map.entry(SHAPES + ".inALambda(java.util.List)", 1),
                Map.entry(SHAPES + ".inAnAnonymousClass(boolean)", 1),
                Map.entry(SHAPES + ".inAnAnonymousClassInALambda(boolean)", 1),
                Map.entry(SHAPES + ".endingALoopBody(java.util.List, boolean)", 1),
                Map.entry(SHAPES + ".breakingOutOfALoop(java.util.List, boolean)", 1),
                Map.entry(SHAPES + ".termsOfALoopCondition(boolean)", 1),
                Map.entry(SHAPES + ".numberLabels(int)", 1),
                Map.entry(SHAPES + ".overloaded(java.lang.String)", 1),
                Map.entry(SHAPES + ".overloaded([Ljava.lang.String;)", 2),
                Map.entry(SHAPES + ".overloaded([[I)", 3),
                Map.entry(SHAPES + ".overloaded(java.lang.Comparable)", 4),
                Map.entry(SHAPES + ".overloaded(java.lang.Object, java.util.Map$Entry)", 1),
                Map.entry(SHAPES + ".varargs([Ljava.lang.String;)", 1),
                Map.entry(SHAPES + "$1Local.<init>(" + SHAPES + ", int, boolean)", 2),
                Map.entry(SHAPES + "$1Local.run()", 1),
                Map.entry(SHAPES + "$1StaticLocal.<init>(java.lang.String, boolean, " + SHELLS + "$Port)", 1),
                Map.entry(SHAPES + "$Inner.<init>(" + SHAPES + ", boolean)", 1),
                Map.entry(SHAPES + "$Level.<init>(java.lang.String, int, " + SHELLS + "$Port)", 1),
                Map.entry(SHAPES + "$Compact.<init>(" + SHELLS + "$Port, java.util.List)", 1)));
    }

    @Test
    void raised_WhenAFrozenMethodGainsABranch_NamesItWithBothCounts() {
        String method = SHAPES + ".overloaded([[I)";
        SortedMap<String, Integer> stored = with(method, 2);

        assertThat(ShellBranches.raised(stored, COUNTED)).containsExactly(method + " 2 -> 3");
        assertThat(ShellBranches.stale(stored, COUNTED)).isEmpty();
    }

    @Test
    void raised_WhenAnUnlistedMethodBranches_NamesIt() {
        SortedMap<String, Integer> stored = new TreeMap<>(COUNTED);
        stored.remove(LATE);

        assertThat(ShellBranches.raised(stored, COUNTED)).containsExactly(LATE + " 0 -> 1");
    }

    @Test
    void stale_WhenAFrozenMethodLosesABranch_NamesItUntilTheLineReadsTheNewCount() {
        assertThat(ShellBranches.stale(with(SAVE, 2), COUNTED)).containsExactly(SAVE + " 2 -> 1");
        assertThat(ShellBranches.raised(with(SAVE, 2), COUNTED)).isEmpty();
        assertThat(ShellBranches.stale(with(SAVE, 1), COUNTED)).isEmpty();
    }

    @Test
    void stale_WhenAStoredMethodIsGoneOrHasNoBranch_NamesIt() {
        assertThat(ShellBranches.stale(with(ACT, 1), COUNTED)).containsExactly(ACT + " 1 -> 0");
        assertThat(ShellBranches.stale(with(SHELLS + ".gone()", 1), COUNTED))
                .containsExactly(SHELLS + ".gone() 1 -> 0");
    }

    @Test
    void in_WhenTwoSourceMethodsShareAKey_FailsNamingIt() throws IOException {
        Files.writeString(directory.resolve("Twins.java"), """
                package twins;
                class Twins {
                    void one() { class Local { void run(boolean x) { if (x) { one(); } } } }
                    void two() { class Local { void run(boolean x) { if (x) { two(); } } } }
                }
                """);

        assertThatThrownBy(() -> ShellBranches.in(FIXTURES, directory, FIXTURE_PACKAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("twins.Twins$Local.run(boolean) has branches but is declared 2 time(s)");
    }

    @Test
    void in_WhenASourceMethodWasNotCompiled_FailsNamingIt() throws IOException {
        Files.writeString(directory.resolve("Absent.java"), """
                package absent;
                class Absent {
                    void run(boolean x) { if (x) { run(false); } }
                }
                """);

        assertThatThrownBy(() -> ShellBranches.in(FIXTURES, directory, FIXTURE_PACKAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent.Absent.run(boolean)")
                .hasMessageContaining("compiled to []");
    }

    @Test
    void in_WhenASourceDoesNotParse_Fails() throws IOException {
        Files.writeString(directory.resolve("Broken.java"), "class Broken { void run( }");

        assertThatThrownBy(() -> ShellBranches.in(FIXTURES, directory, FIXTURE_PACKAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot parse");
    }

    @Test
    void regenerate_WhenALineWouldBeAddedOrRaisedWithoutAReason_IsRefusedAndWritesNothing() {
        SortedMap<String, Integer> stored = with(SAVE, 2);
        stored.remove(LATE);
        Path store = written(stored);

        assertThatThrownBy(() -> ShellBranches.regenerate(store, COUNTED, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(LATE + " 0 -> 1")
                .hasMessageNotContaining(SAVE);
        assertThat(ShellBranches.read(store)).isEqualTo(stored);
    }

    @Test
    void regenerate_WhenGivenAReason_AddsTheLine() {
        SortedMap<String, Integer> stored = new TreeMap<>(COUNTED);
        stored.remove(LATE);
        Path store = written(stored);

        ShellBranches.regenerate(store, COUNTED, "the port moved");

        assertThat(ShellBranches.read(store)).isEqualTo(COUNTED);
    }

    @Test
    void regenerate_WhenLinesOnlyShrink_NeedsNoReason() {
        Path store = written(with(SAVE, 2));

        ShellBranches.regenerate(store, COUNTED, "");

        assertThat(ShellBranches.read(store)).isEqualTo(COUNTED);
    }

    @Test
    void read_WhenALineHasNoCount_FailsNamingIt() throws IOException {
        Path store = Files.writeString(directory.resolve("store.txt"), SAVE + "\n");

        assertThatThrownBy(() -> ShellBranches.read(store))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SAVE);
    }

    @Test
    void read_WhenAMethodIsListedTwice_FailsNamingIt() throws IOException {
        Path store = Files.writeString(directory.resolve("store.txt"), SAVE + " # 1\n" + SAVE + " # 2\n");

        assertThatThrownBy(() -> ShellBranches.read(store))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SAVE + " more than once");
    }

    private static SortedMap<String, Integer> mainBranches() {
        return Main.BRANCHES;
    }

    private static SortedMap<String, Integer> with(String method, int count) {
        SortedMap<String, Integer> stored = new TreeMap<>(COUNTED);
        stored.put(method, count);
        return stored;
    }

    private static SortedMap<String, Integer> countedIn(String fixture) {
        SortedMap<String, Integer> counted = new TreeMap<>(COUNTED);
        counted.keySet().removeIf(method -> !method.startsWith(fixture));
        return counted;
    }

    private Path written(SortedMap<String, Integer> branches) {
        Path store = directory.resolve("written.txt");
        ShellBranches.write(store, branches);
        return store;
    }

    private static final class Main {

        private static final SortedMap<String, Integer> BRANCHES = ShellBranches.in(ImportedClasses.production(),
                ShellBranches.MAIN_SOURCES, ShellBranches.MAIN_PACKAGE);
    }
}
