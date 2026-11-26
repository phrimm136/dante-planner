package org.danteplanner.backend.architecture;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableEntityRuleTest {

    static final ArchRule EVERY_ENTITY_IS_IMMUTABLE =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should().beAnnotatedWith(Immutable.class)
                    .as("every @Entity is @org.hibernate.annotations.Immutable: a change to a stored row is a"
                            + " conditional command, never a field set on a loaded entity");

    static final ArchRule IMMUTABLE_ENTITY_FIELDS_ARE_WRITTEN_ONLY_IN_CONSTRUCTION =
            noClasses()
                    .should().setFieldWhere(writesAnImmutableEntityOutsideItsConstructionAndLifecycle())
                    .as("no field of an @org.hibernate.annotations.Immutable entity is written outside its own"
                            + " constructors and @PrePersist, @PostPersist and @PostLoad methods: a field set on"
                            + " an immutable entity is silently never written");

    private static final String FIXTURE = WriteRuleFixtures.class.getName();
    private static final String MUTABLE = FIXTURE + "$MutableRow";
    private static final String PACKAGE_PRIVATE = FIXTURE + "$PackagePrivateMutableRow";
    private static final String LOOKALIKE = FIXTURE + "$LookalikeRow";
    private static final String LIFECYCLE = FIXTURE + "$LifecycleRow";
    private static final String WRITTEN = FIXTURE + "$WrittenRow";

    @Test
    void production_WhenScanned_HoldsOnlyImmutableEntities() {
        assertThatCode(() -> EVERY_ENTITY_IS_IMMUTABLE.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void production_WhenScanned_WritesNoImmutableEntityFieldOutsideConstruction() {
        assertThatCode(() -> IMMUTABLE_ENTITY_FIELDS_ARE_WRITTEN_ONLY_IN_CONSTRUCTION.check(
                ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void mutableEntity_WhenScanned_FailsByName() {
        assertThatThrownBy(() -> EVERY_ENTITY_IS_IMMUTABLE.check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(MUTABLE)
                .hasMessageContaining(PACKAGE_PRIVATE)
                .hasMessageContaining(LOOKALIKE)
                .hasMessageNotContaining(LIFECYCLE)
                .hasMessageContaining("(3 times)");
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
