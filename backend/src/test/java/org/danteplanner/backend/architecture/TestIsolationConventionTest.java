package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The data-ownership rules the integration tier depends on, checked by scan rather than by review.
 *
 * <p>Every integration class shares one database and they run concurrently, so a test owns the rows
 * it creates and nothing else. Each rule below cost a debugging session before it was written down;
 * a scan catches the violation in seconds where the suite catches it in minutes, intermittently,
 * and usually in some other class.</p>
 */
class TestIsolationConventionTest {

    private static final JavaClasses TEST_CLASSES =
            new ClassFileImporter().importPackages("org.danteplanner.backend");

    @Test
    @DisplayName("only a class owning its database truncates a table")
    void truncation_WhenClassSharesTheDatabase_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                .filter(TestIsolationConventionTest::truncatesATable)
                .filter(clazz -> !mentions(clazz, "registerOwnDatabase"))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("these truncate the shared database, deleting rows their concurrent neighbours "
                        + "are still using; narrow the assertion to rows the test created, or take "
                        + "registerOwnDatabase")
                .isEmpty();
    }

    /**
     * A {@code DELETE} or {@code TRUNCATE} carrying no {@code WHERE}, as the driver receives it.
     *
     * <p>The rule above scans for {@code deleteAll}, a method name the constant pool carries. Raw
     * SQL has the same effect under a name no scan recognizes, so it is matched as text; a
     * statement naming the row it removes is left alone.</p>
     */
    private static final java.util.regex.Pattern UNSCOPED_DELETE = java.util.regex.Pattern.compile(
            "\"\\s*(?:DELETE\\s+FROM|TRUNCATE)(?![^\"]*\\bWHERE\\b)[^\"]*\"",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("only a class owning its database empties a table in SQL")
    void unscopedDeletion_WhenClassSharesTheDatabase_IsRejected() {
        List<String> offenders = containerizedSources().entrySet().stream()
                .filter(entry -> boundToTheSharedDatabase(entry.getValue()))
                .filter(entry -> UNSCOPED_DELETE.matcher(entry.getValue()).find())
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("these empty a table their concurrent neighbours are still reading; add a WHERE "
                        + "naming the test's own rows, or take registerOwnDatabase")
                .isEmpty();
    }

    /** True for a class drawing on the fork's shared database rather than one of its own. */
    private static boolean boundToTheSharedDatabase(String source) {
        return (source.contains("extends SharedMySqlContainerSupport")
                        || source.contains("registerSharedMysql"))
                && !source.contains("registerOwnDatabase");
    }

    /**
     * Redis calls whose scope is the whole keyspace, as owner to method names.
     *
     * <p>A blacklist entry is keyed by a hash of its own token, so classes sharing the fork's Redis
     * cannot collide on one. A scan is the exception: it counts and deletes keys written by classes
     * it has never heard of, and no id exists to narrow it to.</p>
     */
    private static final java.util.Map<String, java.util.Set<String>> KEYSPACE_WIDE_CALLS =
            java.util.Map.of(
                    "org.danteplanner.backend.auth.token.TokenBlacklistService",
                    java.util.Set.of("clear", "size", "userInvalidationSize"),
                    "org.springframework.data.redis.connection.RedisServerCommands",
                    java.util.Set.of("flushAll", "flushDb"));

    @Test
    @DisplayName("only a class owning its Redis scans the whole keyspace")
    void keyspaceScan_WhenClassSharesTheRedis_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::scansTheKeyspace)
                .map(TestIsolationConventionTest::outermost)
                .filter(TestIsolationConventionTest::isTestClass)
                .filter(clazz -> !mentions(clazz, "com/redis/testcontainers/RedisContainer"))
                .map(JavaClass::getSimpleName)
                .distinct()
                .sorted()
                .toList();

        assertThat(offenders)
                .as("these delete or count keys their concurrent neighbours wrote; assert about the "
                        + "token or user the test owns, or start a RedisContainer of its own")
                .isEmpty();
    }

    private static boolean scansTheKeyspace(JavaClass clazz) {
        return clazz.getMethodCallsFromSelf().stream()
                .anyMatch(call -> KEYSPACE_WIDE_CALLS
                        .getOrDefault(call.getTarget().getOwner().getName(), java.util.Set.of())
                        .contains(call.getTarget().getName()));
    }

    /**
     * The top-level class enclosing a {@code @Nested} one. A nested class holds the calls while the
     * container that exempts them is a field on the outer class, and its simple name ends in
     * {@code Tests}, which {@link #isTestClass} does not accept.
     */
    private static JavaClass outermost(JavaClass clazz) {
        JavaClass current = clazz;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }

    @Test
    @DisplayName("no test class carries @Execution")
    void executionMode_WhenDeclaredOnAClass_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                .filter(clazz -> mentions(clazz, "org/junit/jupiter/api/parallel/Execution"))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("the mode propagates to a class's methods, and concurrent methods share the "
                        + "class's @BeforeEach fixtures; the classes-versus-methods split is "
                        + "expressible only in junit-platform.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("a test that drives HTTP does not roll back")
    void committingTier_WhenDeclaringTransactional_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                // Tests that drive HTTP: the endpoint owns the transaction boundary, so wrapping
                // one in a test transaction tests a path production never runs. A repository test
                // may keep @Transactional, because there the boundary is the subject.
                .filter(clazz -> clazz.isAnnotatedWith(
                        "org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc"))
                .filter(clazz -> clazz.isAnnotatedWith("org.springframework.transaction.annotation.Transactional"))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("rollback suppresses the AFTER_COMMIT effects these tests exist to cover, hides "
                        + "their rows from the FULLTEXT index, and binds a transaction to a thread "
                        + "JUnit's executor may hand to another test")
                .isEmpty();
    }

    /**
     * Excludes this class: the tokens it searches for are literals in its own constant pool, so a
     * scanner that scans itself reports itself.
     */
    @Test
    @DisplayName("a class declares at most one @BeforeEach")
    void setup_WhenDeclaredMoreThanOnce_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                .filter(clazz -> clazz.getMethods().stream()
                        .filter(method -> method.isAnnotatedWith("org.junit.jupiter.api.BeforeEach"))
                        .count() > 1)
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("JUnit does not order sibling @BeforeEach methods, so a second one can run "
                        + "after the setup it was meant to precede and delete its fixtures")
                .isEmpty();
    }

    @Test
    @DisplayName("users are built through TestDataFactory, never by hand")
    void userFixture_WhenBuiltByHand_IsRejected() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                // Only where a user is persisted. A Mockito unit test builds one in memory, and an
                // in-memory fixture has no shared database to collide in.
                .filter(clazz -> clazz.isAnnotatedWith("org.springframework.boot.test.context.SpringBootTest"))
                .filter(clazz -> clazz.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dep -> dep.getTargetClass().getName()
                                .equals("org.danteplanner.backend.user.entity.User$UserBuilder")))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("a hand-built User carries whatever email the author typed, and two classes "
                        + "typing the same one collide in the shared database; TestDataFactory "
                        + "sub-addresses every address it issues")
                .isEmpty();
    }

    /**
     * A literal in a shared namespace reads as a constant and is really a claim of exclusive
     * ownership. Three shapes have cost a debugging session each: a foreign key
     * ({@code actorId(1L)}), a UNIQUE column ({@code usernameSuffix("00001")}), and the user id a
     * service derives a Redis key from ({@code uinv:4242}). The value must come from a sequence,
     * so no second writer can pick the same one.
     */
    private static final java.util.regex.Pattern LITERAL_IDENTITY = java.util.regex.Pattern.compile(
            "\\.(actorId|usernameSuffix|targetUuid)\\(\\s*[\"0-9]"
                    + "|\\b(?:Long|long)\\s+\\w*(?:[Uu]serId|[Aa]ctorId|[Oo]wnerId|[Vv]iewerId)\\s*=\\s*[0-9]+L");

    @Test
    @DisplayName("identities in shared namespaces come from a sequence, not a literal")
    void sharedIdentity_WhenHardCoded_IsRejected() {
        List<String> offenders = containerizedSources().entrySet().stream()
                // A class that stands up its own infrastructure owns the namespace it writes into,
                // so a literal there claims nothing anyone else can claim.
                .filter(entry -> !entry.getValue().contains("@DynamicPropertySource")
                        && !entry.getValue().contains("new RedisContainer")
                        && !entry.getValue().contains("new GenericContainer"))
                .filter(entry -> LITERAL_IDENTITY.matcher(entry.getValue()).find())
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("a hard-coded id claims a name every concurrently running class may also claim; "
                        + "take TestDataFactory.nextUserId() or uniqueSuffix(), which cannot collide")
                .isEmpty();
    }

    /**
     * An {@code @AfterEach} whose body clears the security context.
     *
     * <p>Clearing only in setup protects the class that does it and nobody else. The holder's
     * default strategy is a {@code ThreadLocal} that outlives the class, MockMvc runs its filter
     * chain on the calling thread, and {@code JwtAuthenticationFilter} sets a principal only when
     * none is present — so a leftover authentication makes a later class's request run as this
     * test's user, which surfaces as a 403, a 404, or a response carrying a stranger's data.</p>
     */
    private static final java.util.regex.Pattern CLEARS_AFTER_EACH = java.util.regex.Pattern.compile(
            "@AfterEach\\b[^{]*\\{[^}]*clearContext", java.util.regex.Pattern.DOTALL);

    @Test
    @DisplayName("a test filling the security context clears it on the way out")
    void securityContext_WhenNotClearedOnExit_IsRejected() {
        List<String> offenders = testSources().entrySet().stream()
                // Excludes this file: the token it searches for is a literal in its own source, so
                // a scanner that scans itself reports itself.
                .filter(entry -> !entry.getKey().equals("TestIsolationConventionTest.java"))
                .filter(entry -> entry.getValue().contains("SecurityContextHolder"))
                .filter(entry -> !CLEARS_AFTER_EACH.matcher(entry.getValue()).find())
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("these leave an Authentication on the thread for whatever runs next; clear it "
                        + "in an @AfterEach, not only in setup")
                .isEmpty();
    }

    /** Source text of every class tagged containerized, keyed by file name. */
    private static java.util.Map<String, String> containerizedSources() {
        return testSources().entrySet().stream()
                .filter(entry -> entry.getValue().contains("@Tag(\"containerized\")"))
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    /** Source text of every test class, keyed by file name. */
    private static java.util.Map<String, String> testSources() {
        try (java.util.stream.Stream<java.nio.file.Path> files =
                java.nio.file.Files.walk(java.nio.file.Path.of("src/test/java"))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toMap(
                            path -> path.getFileName().toString(),
                            TestIsolationConventionTest::readSource));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the test sources", e);
        }
    }

    private static String readSource(java.nio.file.Path path) {
        try {
            return java.nio.file.Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    /**
     * Each class declaring its own {@code @DynamicPropertySource} keys a distinct application
     * context. Past the cache bound Spring evicts, and eviction closes a context while other
     * classes are still running against it — which surfaces as rows that vanish mid-test, in some
     * unrelated class, only at full suite size. Raising the bound defers that; keeping the count
     * low is what prevents it. The budget sits at the current count, so any addition is a
     * deliberate decision rather than a silent slide toward the bound.
     */
    private static final int CONTEXT_BUDGET = 29;

    @Test
    @DisplayName("the suite stays within its application-context budget")
    void distinctContexts_WhenOverBudget_IsRejected() {
        List<String> forcing = TEST_CLASSES.stream()
                .filter(clazz -> clazz.getMethods().stream()
                        .anyMatch(method -> method.isAnnotatedWith(
                                "org.springframework.test.context.DynamicPropertySource")))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(forcing)
                .as("each of these keys its own context; share one by dropping the property source, "
                        + "or raise both this budget and spring.test.context.cache.maxSize together")
                .hasSizeLessThanOrEqualTo(CONTEXT_BUDGET);
    }

    private static boolean isTestClass(JavaClass clazz) {
        if (clazz.getName().equals(TestIsolationConventionTest.class.getName())) {
            return false;
        }
        return clazz.getSimpleName().endsWith("Test") || clazz.getSimpleName().endsWith("IT");
    }

    /**
     * Spring Data's whole-table deletions. Matched as exact call targets rather than by name
     * fragment: a scoped sibling like {@code deleteAllByPlannerIds} names the rows it removes and
     * is the correct way to clean up, yet it contains {@code deleteAll} and a substring search
     * rejects it.
     */
    private static final java.util.Set<String> WHOLE_TABLE_DELETES =
            java.util.Set.of("deleteAll", "deleteAllInBatch");

    private static boolean truncatesATable(JavaClass clazz) {
        return clazz.getMethodCallsFromSelf().stream()
                .anyMatch(call -> WHOLE_TABLE_DELETES.contains(call.getTarget().getName())
                        && call.getTarget().getRawParameterTypes().isEmpty());
    }

    /**
     * True when the class file's constant pool carries the token. ArchUnit models method bodies as
     * accesses rather than literals, so the bytes are searched directly; a string literal and a
     * referenced type name both appear there as UTF-8.
     */
    private static boolean mentions(JavaClass clazz, String token) {
        byte[] bytecode = readClassFile(clazz);
        byte[] probe = token.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= bytecode.length - probe.length; i++) {
            for (int j = 0; j < probe.length; j++) {
                if (bytecode[i + j] != probe[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] readClassFile(JavaClass clazz) {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = TestIsolationConventionTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new byte[0];
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the class file for " + clazz.getName(), e);
        }
    }
}
