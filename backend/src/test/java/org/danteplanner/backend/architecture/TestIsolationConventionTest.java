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
    void truncation_is_confined_to_classes_that_own_their_database() {
        List<String> offenders = TEST_CLASSES.stream()
                .filter(TestIsolationConventionTest::isTestClass)
                .filter(clazz -> mentions(clazz, "deleteAll"))
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

    @Test
    @DisplayName("no test class carries @Execution")
    void execution_mode_is_never_declared_on_a_class() {
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
    void the_committing_tier_does_not_declare_transactional() {
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
    void setup_is_declared_once_per_class() {
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
    void user_fixtures_come_from_the_factory() {
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
     * Each class declaring its own {@code @DynamicPropertySource} keys a distinct application
     * context. Past the cache bound Spring evicts, and eviction closes a context while other
     * classes are still running against it — which surfaces as rows that vanish mid-test, in some
     * unrelated class, only at full suite size. Raising the bound defers that; keeping the count
     * low is what prevents it. The budget sits at the current count, so any addition is a
     * deliberate decision rather than a silent slide toward the bound.
     */
    private static final int CONTEXT_BUDGET = 26;

    @Test
    @DisplayName("the suite stays within its application-context budget")
    void distinct_contexts_stay_within_budget() {
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
