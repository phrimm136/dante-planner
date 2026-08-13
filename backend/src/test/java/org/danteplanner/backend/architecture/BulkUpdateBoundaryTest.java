package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps bulk UPDATEs off the repositories of version-checked rows.
 *
 * <p>A {@code @Version} column is a claim that every write to the row was made against the state
 * the writer had read. A bulk UPDATE is issued straight to the database: it neither reads the
 * version nor raises it, so a concurrent owner's save still commits against a version the bulk
 * statement has already overwritten, and the conflict the column exists to surface never
 * materializes. The two writers end up merged silently, with the loser's edit gone.</p>
 *
 * <p>Scoped to UPDATE, and deliberately not to every bulk statement: a bulk DELETE removes the row
 * and its version together, leaving no state for a later write to be checked against, which is why
 * the account-deletion sweeps stay green.</p>
 *
 * <p>Which rows are version-checked is read from the code rather than listed here: an entity
 * declaring a {@code @Version} field, reached from a repository through the type argument of its
 * generic supertype. A repository added tomorrow is covered the moment it names the entity.</p>
 *
 * <p>The rule body is shared with a fixture that violates it on purpose. A rule only ever asserted
 * against a passing codebase demonstrates that the codebase passes, not that the rule rejects
 * anything, and a rule that matches nothing leaves the first test green and the second red.</p>
 */
class BulkUpdateBoundaryTest {

    private static final String VERSION = "jakarta.persistence.Version";

    private static final String MODIFYING = "org.springframework.data.jpa.repository.Modifying";

    private static final String QUERY = "org.springframework.data.jpa.repository.Query";

    private static final String FIXTURE_PACKAGE = "org.danteplanner.backend.architecture.fixture";

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter()
            .importPackages(FIXTURE_PACKAGE);

    @Test
    @DisplayName("no repository of a version-checked row issues a bulk UPDATE")
    void bulkUpdate_WhenIssuedOnAVersionedRow_IsRejected() {
        assertThat(offendersIn(MAIN_CLASSES))
                .as("a statement the database applies without reading the version leaves the "
                        + "concurrent owner's save to commit over it; load the rows and let the "
                        + "version check run, or move the column off the entity")
                .isEmpty();
    }

    @Test
    @DisplayName("the rule names the offending method, and leaves the bulk delete beside it alone")
    void offendingMethod_WhenTheRuleRunsOverTheFixture_IsNamed() {
        assertThat(offendersIn(FIXTURE_CLASSES))
                .as("the rule above passes vacuously unless this one shows it rejecting a bulk "
                        + "UPDATE, and naming only the UPDATE is what keeps the sweeps green")
                .containsExactly(FIXTURE_PACKAGE + ".VersionedRowBulkUpdater$VersionedRows.renameAll");
    }

    /**
     * Every method in the given classes that updates rows in bulk on behalf of a version-checked
     * entity.
     *
     * @param classes the classes to scan
     * @return the offenders as {@code owner.method}, sorted, empty when there are none
     */
    private static Set<String> offendersIn(JavaClasses classes) {
        Set<String> offenders = new TreeSet<>();
        for (JavaClass clazz : classes) {
            if (!managesAVersionedRow(clazz)) {
                continue;
            }
            for (JavaMethod method : clazz.getMethods()) {
                if (isBulkUpdate(method)) {
                    offenders.add(clazz.getFullName() + "." + method.getName());
                }
            }
        }
        return offenders;
    }

    private static boolean managesAVersionedRow(JavaClass clazz) {
        return genericSupertypeArgumentsOf(clazz).stream()
                .anyMatch(BulkUpdateBoundaryTest::declaresAVersion);
    }

    /**
     * The type arguments a class receives from its generic supertypes, at any depth — for a Spring
     * Data repository, the entity it manages and that entity's key.
     *
     * @param clazz the class to resolve
     * @return the erasures of those arguments
     */
    private static Set<JavaClass> genericSupertypeArgumentsOf(JavaClass clazz) {
        Set<JavaClass> arguments = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<JavaClass> pending = new ArrayDeque<>();
        pending.add(clazz);

        while (!pending.isEmpty()) {
            JavaClass current = pending.remove();
            if (!visited.add(current.getFullName())) {
                continue;
            }
            for (JavaType supertype : current.getInterfaces()) {
                if (supertype instanceof JavaParameterizedType parameterized) {
                    parameterized.getActualTypeArguments()
                            .forEach(argument -> arguments.add(argument.toErasure()));
                }
                pending.add(supertype.toErasure());
            }
        }
        return arguments;
    }

    private static boolean declaresAVersion(JavaClass type) {
        return type.getAllFields().stream().anyMatch(field -> field.isAnnotatedWith(VERSION));
    }

    private static boolean isBulkUpdate(JavaMethod method) {
        return method.isAnnotatedWith(MODIFYING)
                && queryOf(method).map(BulkUpdateBoundaryTest::isUpdateStatement).orElse(false);
    }

    private static Optional<String> queryOf(JavaMethod method) {
        return method.tryGetAnnotationOfType(QUERY)
                .flatMap(annotation -> annotation.get("value"))
                .map(Object::toString);
    }

    private static boolean isUpdateStatement(String query) {
        return query.stripLeading().toUpperCase(Locale.ROOT).startsWith("UPDATE");
    }
}
