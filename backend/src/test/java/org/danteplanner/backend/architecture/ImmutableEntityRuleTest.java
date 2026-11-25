package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.danteplanner.fixture.bulkupdate.WriteRuleFixtures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableEntityRuleTest {

    static final ArchRule IMMUTABLE_ENTITY_FIELDS_ARE_WRITTEN_ONLY_IN_CONSTRUCTION =
            noClasses()
                    .should().setFieldWhere(writesAnImmutableEntityOutsideItsConstructionAndLifecycle())
                    .as("no field of an @org.hibernate.annotations.Immutable entity is written outside its own"
                            + " constructors and @PrePersist, @PostPersist and @PostLoad methods: a field set on"
                            + " an immutable entity is silently never written");

    private static final String LIST_PREFIX = "mutable-entities-";
    private static final String FIXTURE = WriteRuleFixtures.class.getName();
    private static final String MUTABLE = FIXTURE + "$MutableRow";
    private static final String PACKAGE_PRIVATE = FIXTURE + "$PackagePrivateMutableRow";
    private static final String LOOKALIKE = FIXTURE + "$LookalikeRow";
    private static final String LIFECYCLE = FIXTURE + "$LifecycleRow";
    private static final String WRITTEN = FIXTURE + "$WrittenRow";
    private static final String LIST = LIST_PREFIX + "bulkupdate.txt";

    @Test
    void production_WhenScanned_HoldsExactlyTheListedMutableEntities() {
        assertThatCode(() -> mutableEntitiesAreExactly(moduleLists(LIST_PREFIX)).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void lists_WhenRead_AreSortedWithoutDuplicatesAndHoldOnlyTheirModule() {
        assertThat(defectsIn(moduleLists(LIST_PREFIX), LIST_PREFIX)).isEmpty();
    }

    @Test
    void production_WhenScanned_WritesNoImmutableEntityFieldOutsideConstruction() {
        assertThatCode(() -> IMMUTABLE_ENTITY_FIELDS_ARE_WRITTEN_ONLY_IN_CONSTRUCTION.check(
                ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void mutableEntity_WhenNotListed_FailsByName() {
        assertThatThrownBy(() -> mutableEntitiesAreExactly(Map.of()).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unlisted " + MUTABLE)
                .hasMessageContaining("unlisted " + PACKAGE_PRIVATE)
                .hasMessageContaining("unlisted " + LOOKALIKE)
                .hasMessageContaining("(3 times)");
    }

    @Test
    void mutableEntity_WhenListed_Passes() {
        assertThatCode(() -> mutableEntitiesAreExactly(Map.of(LIST, List.of(LOOKALIKE, MUTABLE, PACKAGE_PRIVATE)))
                .check(fixtureClasses()))
                .doesNotThrowAnyException();
    }

    @Test
    void listedEntity_WhenImmutableOrGone_FailsByItsLine() {
        assertThatThrownBy(() -> mutableEntitiesAreExactly(Map.of(LIST,
                List.of("gone.Row", LIFECYCLE, LOOKALIKE, MUTABLE, PACKAGE_PRIVATE))).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete " + LIFECYCLE + " from backend/config/" + LIST)
                .hasMessageContaining("delete gone.Row from backend/config/" + LIST)
                .hasMessageContaining("(2 times)");
    }

    @Test
    void list_WhenUnsortedDuplicatedEmptyOrOfAnotherModule_IsADefect() {
        String first = "org.danteplanner.backend.user.A";
        String second = "org.danteplanner.backend.user.B";
        String list = LIST_PREFIX + "user.txt";

        assertThat(defectsIn(Map.of(list, List.of(first, second)), LIST_PREFIX)).isEmpty();
        assertThat(defectsIn(Map.of(list, List.of(second, first)), LIST_PREFIX))
                .containsExactly(list + " is not sorted without duplicates");
        assertThat(defectsIn(Map.of(list, List.of(first, first)), LIST_PREFIX))
                .containsExactly(list + " is not sorted without duplicates");
        assertThat(defectsIn(Map.of(list, List.of()), LIST_PREFIX))
                .containsExactly(list + " is empty; delete it");
        assertThat(defectsIn(Map.of(list, List.of("org.danteplanner.backend.userland.A")), LIST_PREFIX))
                .containsExactly(list + " holds org.danteplanner.backend.userland.A of another module");
    }

    @Test
    void immutableEntityField_WhenWrittenOutsideConstruction_FailsByTheWriter() {
        assertThatThrownBy(() -> IMMUTABLE_ENTITY_FIELDS_ARE_WRITTEN_ONLY_IN_CONSTRUCTION.check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(WRITTEN + ".setTitle(")
                .hasMessageContaining(WRITTEN + ".rename(")
                .hasMessageContaining(FIXTURE + "$Neighbour.<init>(")
                .hasMessageContaining(FIXTURE + "$Neighbour.annotate(")
                .hasMessageContaining(FIXTURE + "$Neighbour.stamp(")
                .hasMessageContaining("(5 times)")
                .hasMessageNotContaining(LIFECYCLE)
                .hasMessageNotContaining(MUTABLE);
    }

    static SortedMap<String, List<String>> moduleLists(String prefix) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        try (Stream<Path> files = Files.list(Path.of("config"))) {
            files.filter(file -> file.getFileName().toString().startsWith(prefix))
                    .forEach(file -> lists.put(file.getFileName().toString(), ShellBranches.lines(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lists;
    }

    static List<String> defectsIn(Map<String, List<String>> lists, String prefix) {
        List<String> defects = new ArrayList<>();
        lists.forEach((file, lines) -> {
            if (lines.isEmpty()) {
                defects.add(file + " is empty; delete it");
            }
            if (!lines.equals(List.copyOf(new TreeSet<>(lines)))) {
                defects.add(file + " is not sorted without duplicates");
            }
            String module = file.substring(prefix.length(), file.length() - ".txt".length());
            lines.stream()
                    .filter(line -> !line.startsWith("org.danteplanner.backend." + module + "."))
                    .forEach(line -> defects.add(file + " holds " + line + " of another module"));
        });
        return defects;
    }

    static <T extends HasName.AndFullName & HasDescription> ArchCondition<T> beListed(
            Map<String, List<String>> lists, Predicate<? super T> exempted) {
        return new ArchCondition<>("be listed exactly while they need it") {

            private final Set<String> found = new LinkedHashSet<>();

            @Override
            public void check(T item, ConditionEvents events) {
                if (exempted.test(item)) {
                    return;
                }
                found.add(item.getFullName());
                if (lists.values().stream().flatMap(Collection::stream).noneMatch(item.getFullName()::equals)) {
                    events.add(SimpleConditionEvent.violated(item, "unlisted " + item.getFullName()));
                }
            }

            @Override
            public void finish(ConditionEvents events) {
                lists.forEach((file, lines) -> lines.stream()
                        .filter(line -> !found.contains(line))
                        .forEach(line -> events.add(SimpleConditionEvent.violated(line,
                                "delete " + line + " from backend/config/" + file))));
            }
        };
    }

    static JavaClasses fixtureClasses() {
        return new ClassFileImporter().importPackages(WriteRuleFixtures.class.getPackageName());
    }

    private static ArchRule mutableEntitiesAreExactly(Map<String, List<String>> lists) {
        return classes()
                .that().areAnnotatedWith(Entity.class)
                .should(ImmutableEntityRuleTest.<JavaClass>beListed(lists,
                        entity -> entity.isAnnotatedWith(Immutable.class)))
                .as("every @Entity is @org.hibernate.annotations.Immutable except exactly the lines of"
                        + " backend/config/" + LIST_PREFIX + "MODULE.txt, which only shrink: an unlisted entity is"
                        + " made immutable, not added");
    }

    private static DescribedPredicate<JavaFieldAccess> writesAnImmutableEntityOutsideItsConstructionAndLifecycle() {
        return DescribedPredicate.describe(
                "a field of an @Immutable entity, outside its constructors and lifecycle callbacks",
                access -> access.getTargetOwner().isAnnotatedWith(Entity.class)
                        && access.getTargetOwner().isAnnotatedWith(Immutable.class)
                        && !isStatic(access)
                        && !isConstructionOrLifecycleOf(access.getTargetOwner(), access.getOrigin()));
    }

    private static boolean isStatic(JavaFieldAccess access) {
        return access.getTarget().resolveMember()
                .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                .isPresent();
    }

    private static boolean isConstructionOrLifecycleOf(JavaClass entity, JavaCodeUnit origin) {
        return origin.getOwner().equals(entity)
                && (origin.isConstructor()
                        || origin.isAnnotatedWith(PrePersist.class)
                        || origin.isAnnotatedWith(PostPersist.class)
                        || origin.isAnnotatedWith(PostLoad.class));
    }
}
