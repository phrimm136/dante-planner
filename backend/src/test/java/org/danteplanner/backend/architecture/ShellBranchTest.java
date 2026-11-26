package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<JavaClasses, SortedMap<String, Integer>> COUNTS = new ConcurrentHashMap<>();

    private static final SortedMap<String, Integer> COUNTED =
            counted(FIXTURES, FIXTURE_SOURCES, FIXTURE_PACKAGE);

    private static final String SAVE = SHELLS + ".save(boolean)";

    private static final String LATE = ShellBranchFixtures.LateViolation.class.getName() + ".late(boolean)";

    private static final String ACT = SHELLS + ".act(" + SHELLS + "$Verdict)";

    @TempDir
    Path directory;

    @Test
    void mainTree_WhenCounted_MatchesTheStore() {
        assertThat(ShellBranches.moved(ShellBranches.read(ShellBranches.STORE), counted(ImportedClasses.production(),
                ShellBranches.MAIN_SOURCES, ShellBranches.MAIN_PACKAGE)))
                .as("the branch counts of effectful methods moved; run `checks be freeze-branches` so that %s"
                        + " shows them in the diff, where review reads a raised line as a method to look at",
                        ShellBranches.STORE)
                .isEmpty();
    }

    @Test
    void store_WhenRead_IsSorted() {
        assertThat(ShellBranches.lines(ShellBranches.STORE))
                .containsExactlyElementsOf(ShellBranches.lines(written(ShellBranches.read(ShellBranches.STORE))));
    }

    @Test
    void shells_WhenCounted_AreTheEffectfulMethodsWithABranch() {
        assertThat(countedIn(SHELLS)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(LATE, 1),
                Map.entry(ACT.replace(".act(", ".actGuarded(").replace(")", ", boolean)"), 1),
                Map.entry(SHELLS + ".choose(boolean, boolean, boolean)", 1),
                Map.entry(SHELLS + ".drainEitherWay(boolean)", 1),
                Map.entry(SHELLS + ".drainSkippingAfterA(boolean)", 1),
                Map.entry(SHELLS + ".drainWhenAllowed(boolean)", 1),
                Map.entry(SHELLS + ".f(boolean, boolean)", 1),
                Map.entry(SHELLS + ".guardedByAReturn(boolean, boolean)", 2),
                Map.entry(SHELLS + ".guardReturningACall(boolean)", 1),
                Map.entry(SHELLS + ".guardReturningAConstant(boolean)", 1),
                Map.entry(SHELLS + ".ternaryInAGuard(boolean, boolean)", 1),
                Map.entry(SHELLS + ".route(java.lang.String)", 1),
                Map.entry(SHELLS + ".sendEachUnlessEmpty()", 1),
                Map.entry(SHELLS + ".switchOnNull(java.lang.String)", 1),
                Map.entry(SHELLS + ".switchOnAPattern(java.lang.Object)", 1),
                Map.entry(SAVE, 1)));
    }

    @Test
    void shapes_WhenCounted_MatchTheirSourceMethods() {
        assertThat(countedIn(SHAPES)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(SHAPES + ".<init>(" + SHELLS + "$Port, java.util.Queue, boolean)", 1),
                Map.entry(SHAPES + ".inALambda(java.util.List)", 1),
                Map.entry(SHAPES + ".inAnAnonymousClass(boolean)", 1),
                Map.entry(SHAPES + ".inAnAnonymousClassInALambda(boolean)", 1),
                Map.entry(SHAPES + ".endingALoopBody(java.util.List, boolean)", 1),
                Map.entry(SHAPES + ".breakingOutOfALoop(java.util.List, boolean)", 1),
                Map.entry(SHAPES + ".termsOfALoopCondition(boolean)", 1),
                Map.entry(SHAPES + ".numberLabels(int)", 1),
                Map.entry(SHAPES + ".enumLabels(" + SHAPES + "$Level)", 1),
                Map.entry(SHAPES + ".overloaded(java.lang.String)", 1),
                Map.entry(SHAPES + ".overloaded([Ljava.lang.String;)", 2),
                Map.entry(SHAPES + ".overloaded([[I)", 3),
                Map.entry(SHAPES + ".overloaded(java.lang.Comparable)", 4),
                Map.entry(SHAPES + ".overloaded(java.lang.Object, java.util.Map$Entry)", 1),
                Map.entry(SHAPES + ".varargs([Ljava.lang.String;)", 1),
                Map.entry(SHAPES + "$1Local.<init>(" + SHAPES + ", int, boolean)", 2),
                Map.entry(SHAPES + "$1Local.run()", 1),
                Map.entry(SHAPES + "$1StaticLocal.<init>(java.lang.String, boolean, " + SHELLS + "$Port)", 1),
                Map.entry(SHAPES + "$1TypedLocal.<init>(" + SHAPES + ", java.lang.Object)", 1),
                Map.entry(SHAPES + "$EffectfulTwins$.run(Port, boolean)", 3),
                Map.entry(SHAPES + "$FieldAndMethod$.run()", 1),
                Map.entry(SHAPES + "$FieldAndMethod.inAMethod(boolean)", 1),
                Map.entry(SHAPES + "$Inner.<init>(" + SHAPES + ", boolean)", 1),
                Map.entry(SHAPES + "$Level.<init>(java.lang.String, int, " + SHELLS + "$Port)", 1),
                Map.entry(SHAPES + "$Compact.<init>(" + SHELLS + "$Port, java.util.List)", 1)));
    }

    @Test
    void moved_WhenAListedMethodGainsABranch_NamesItWithBothCounts() {
        String method = SHAPES + ".overloaded([[I)";

        assertThat(ShellBranches.moved(with(method, 2), COUNTED)).containsExactly(method + " 2 -> 3");
    }

    @Test
    void moved_WhenAnUnlistedMethodBranches_NamesIt() {
        SortedMap<String, Integer> stored = new TreeMap<>(COUNTED);
        stored.remove(LATE);

        assertThat(ShellBranches.moved(stored, counted(FIXTURES, FIXTURE_SOURCES, FIXTURE_PACKAGE)))
                .containsExactly(LATE + " 0 -> 1");
    }

    @Test
    void moved_WhenAListedMethodLosesABranch_NamesItUntilTheLineReadsTheNewCount() {
        assertThat(ShellBranches.moved(with(SAVE, 2), COUNTED)).containsExactly(SAVE + " 2 -> 1");
        assertThat(ShellBranches.moved(with(SAVE, 1), COUNTED)).isEmpty();
    }

    @Test
    void moved_WhenAStoredMethodIsGoneOrHasNoBranch_NamesIt() {
        assertThat(ShellBranches.moved(with(ACT, 1), counted(FIXTURES, FIXTURE_SOURCES, FIXTURE_PACKAGE)))
                .containsExactly(ACT + " 1 -> 0");
        assertThat(ShellBranches.moved(with(SHELLS + ".gone()", 1), COUNTED))
                .containsExactly(SHELLS + ".gone() 1 -> 0");
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
                .hasMessageContaining("was not compiled");
    }

    @Test
    void in_WhenASourceDoesNotParse_Fails() throws IOException {
        Files.writeString(directory.resolve("Broken.java"), "class Broken { void run( }");

        assertThatThrownBy(() -> ShellBranches.in(FIXTURES, directory, FIXTURE_PACKAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot parse");
    }

    @Test
    void regenerate_WhenCountsMoved_WritesThemAndPrintsEachChange() {
        SortedMap<String, Integer> stored = with(SAVE, 2);
        stored.remove(LATE);
        Path store = written(stored);

        assertThat(ShellBranches.regenerate(store, COUNTED))
                .containsExactly("moved:", LATE + " 0 -> 1", SAVE + " 2 -> 1");
        assertThat(ShellBranches.read(store)).isEqualTo(COUNTED);
    }

    @Test
    void regenerate_WhenNothingMoved_PrintsNothing() {
        Path store = written(COUNTED);

        assertThat(ShellBranches.regenerate(store, COUNTED)).isEmpty();
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

    private static SortedMap<String, Integer> counted(JavaClasses classes, Path sourceRoot, String within) {
        return COUNTS.computeIfAbsent(classes, absent -> ShellBranches.in(classes, sourceRoot, within));
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
}
