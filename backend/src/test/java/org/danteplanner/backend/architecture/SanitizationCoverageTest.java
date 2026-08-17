package org.danteplanner.backend.architecture;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.sanitize.NotUserContent;
import org.danteplanner.backend.shared.sanitize.Sanitized;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the request boundary to its sanitization policy: in the features that accept user-authored
 * text, every String a request DTO carries either declares a sanitizer or declares that it is not
 * user content.
 *
 * <p>This is the enforcement half of the policy. The sanitizers existed for months while nothing
 * called them, because a transformation applied by hand is invisible to every test that does not
 * already know to look for it. A declaration on the component is visible to reflection, so an
 * unannotated String is a build failure rather than a review finding somebody has to catch.</p>
 */
class SanitizationCoverageTest {

    private static final Set<String> USER_CONTENT_DTO_PACKAGES = Set.of(
            "org.danteplanner.backend.planner.dto",
            "org.danteplanner.backend.comment.dto",
            "org.danteplanner.backend.moderation.dto");

    /**
     * The components allowed to carry neither declaration, each named {@code Record.component}.
     *
     * <p>Comment content is HTML today and becomes Tiptap JSON in its own unit; annotating it with
     * the plain-text kind now would strip the markup the current rows depend on. The set is checked
     * for staleness by {@link #pendingException_WhenAlreadyResolved_IsRejected}, so it can only
     * shrink.</p>
     */
    private static final Set<String> PENDING = Set.of(
            "CreateCommentRequest.content",
            "UpdateCommentRequest.content");

    private static final JavaClasses DTO_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(USER_CONTENT_DTO_PACKAGES);

    @Test
    @DisplayName("every String a user-content request DTO carries declares how it is handled")
    void stringComponent_WhenNeitherSanitizedNorOptedOut_IsRejected() {
        List<String> undeclared = undeclaredComponents().stream()
                .filter(name -> !PENDING.contains(name))
                .toList();

        assertThat(undeclared)
                .as("these reach persistence with whatever the caller typed; annotate each with "
                        + "@Sanitized(kind), or with @NotUserContent when the value is an identifier "
                        + "or an enum name the server validates against a closed set")
                .isEmpty();
    }

    @Test
    @DisplayName("a pending exception that no longer applies is removed from the set")
    void pendingException_WhenAlreadyResolved_IsRejected() {
        assertThat(undeclaredComponents())
                .as("a name here that is now annotated, renamed, or deleted makes the exception set "
                        + "a record of history rather than of policy; drop it from PENDING")
                .containsAll(PENDING);
    }

    /** The String components of every request record in scope that declares neither annotation. */
    private static List<String> undeclaredComponents() {
        List<String> names = new ArrayList<>();
        for (Class<?> record : requestRecords()) {
            for (RecordComponent component : record.getRecordComponents()) {
                if (component.getType().equals(String.class) && !declaresHandling(component)) {
                    names.add(record.getSimpleName() + "." + component.getName());
                }
            }
        }
        return names.stream().sorted().toList();
    }

    private static boolean declaresHandling(RecordComponent component) {
        return component.isAnnotationPresent(Sanitized.class)
                || component.isAnnotationPresent(NotUserContent.class);
    }

    private static List<Class<?>> requestRecords() {
        return DTO_CLASSES.stream()
                .filter(clazz -> clazz.getSimpleName().endsWith("Request"))
                .map(JavaClass::reflect)
                .filter(Class::isRecord)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }
}
