package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.danteplanner.fixture.entitynullability.EntityNullabilityFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityNullabilityRuleTest {

    static final ArchRule ENTITY_FIELDS_DECLARE_NULLNESS =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .or().areAnnotatedWith(Embeddable.class)
                    .should(declareTheNullnessOfEveryPersistentField())
                    .as("every persistent entity field is either declared non-null to JPA (an id, nullable = false,"
                            + " optional = false, a collection) or typed @Nullable, matching its column");

    private static final List<Class<? extends Annotation>> COLLECTIONS =
            List.of(OneToMany.class, ManyToMany.class, ElementCollection.class);

    @Test
    void mainEntities_WhenScanned_DeclareTheNullnessOfEveryField() {
        assertThatCode(() -> ENTITY_FIELDS_DECLARE_NULLNESS.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixtureEntity_WhenAFieldDeclaresNoNullnessOrBoth_FailsNamingTheField() {
        String fixture = EntityNullabilityFixture.class.getName();

        assertThatThrownBy(() -> ENTITY_FIELDS_DECLARE_NULLNESS.check(
                new ClassFileImporter().importClasses(EntityNullabilityFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + ".deletedAt declares no nullness")
                .hasMessageContaining(fixture + ".archivedAt is @Nullable on a column declared non-null")
                .hasMessageNotContaining(fixture + ".editedAt")
                .hasMessageNotContaining(fixture + ".name")
                .hasMessageNotContaining(fixture + ".children")
                .hasMessageNotContaining(fixture + ".derived")
                .hasMessageNotContaining(fixture + ".count");
    }

    private static ArchCondition<JavaClass> declareTheNullnessOfEveryPersistentField() {
        return new ArchCondition<>("declare the nullness of every persistent field") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                entity.getFields().stream()
                        .filter(EntityNullabilityRuleTest::isPersistent)
                        .forEach(field -> report(field, events));
            }
        };
    }

    private static boolean isPersistent(JavaField field) {
        return !field.getModifiers().contains(JavaModifier.STATIC)
                && !field.getModifiers().contains(JavaModifier.TRANSIENT)
                && !field.isAnnotatedWith(Transient.class)
                && !field.getRawType().isPrimitive();
    }

    private static void report(JavaField field, ConditionEvents events) {
        boolean nullable = field.reflect().getAnnotatedType().isAnnotationPresent(Nullable.class);
        boolean nonNull = isDeclaredNonNull(field);
        if (nullable && nonNull) {
            events.add(SimpleConditionEvent.violated(field,
                    field.getFullName() + " is @Nullable on a column declared non-null"));
        } else if (!nullable && !nonNull) {
            events.add(SimpleConditionEvent.violated(field, field.getFullName()
                    + " declares no nullness; add nullable = false or type it @Nullable as its column is"));
        }
    }

    private static boolean isDeclaredNonNull(JavaField field) {
        return field.isAnnotatedWith(Id.class)
                || field.isAnnotatedWith(EmbeddedId.class)
                || field.isAnnotatedWith(MapsId.class)
                || COLLECTIONS.stream().anyMatch(field::isAnnotatedWith)
                || field.tryGetAnnotationOfType(Column.class).map(column -> !column.nullable()).orElse(false)
                || field.tryGetAnnotationOfType(JoinColumn.class).map(join -> !join.nullable()).orElse(false)
                || field.tryGetAnnotationOfType(ManyToOne.class).map(relation -> !relation.optional()).orElse(false)
                || field.tryGetAnnotationOfType(OneToOne.class).map(relation -> !relation.optional()).orElse(false);
    }
}
