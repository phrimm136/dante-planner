package org.danteplanner.backend.integration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.VirtualModelPart;
import org.hibernate.persister.entity.EntityPersister;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class EntityNullabilitySchemaIT extends SharedMySqlContainerSupport {

    private static final String NULLABLE_COLUMN = "YES";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void entityField_WhenComparedWithItsMigratedColumn_DeclaresTheColumnsNullability() {
        Map<String, Boolean> columnNullable = columnNullability();
        List<String> mismatches = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel()
                .forEachEntityDescriptor(persister -> {
                    persister.getIdentifierMapping().forEachSelectable((index, selectable) ->
                            compare(persister.getEntityName() + ".<id>", false,
                                    key(selectable.getContainingTableExpression(), selectable.getSelectionExpression()),
                                    columnNullable, mismatches, unmapped));
                    persister.forEachAttributeMapping(attribute ->
                            compareAttribute(persister, persister.getMappedClass(), attribute,
                                    columnNullable, mismatches, unmapped));
                });

        assertThat(unmapped).as("mapped columns the migrated schema does not declare").isEmpty();
        assertThat(mismatches)
                .as("an entity field is @Nullable exactly when its column IS_NULLABLE; fix the field or the migration")
                .isEmpty();
    }

    private void compareAttribute(EntityPersister persister, Class<?> owner, AttributeMapping attribute,
            Map<String, Boolean> columnNullable, List<String> mismatches, List<String> unmapped) {
        if (attribute instanceof PluralAttributeMapping || attribute instanceof VirtualModelPart) {
            return;
        }
        Field field = declaredField(owner, attribute.getAttributeName());
        if (attribute instanceof EmbeddableValuedModelPart embedded) {
            embedded.getEmbeddableTypeDescriptor().forEachAttributeMapping(nested ->
                    compareAttribute(persister, field.getType(), nested, columnNullable, mismatches, unmapped));
            return;
        }
        boolean fieldNullable = isNullable(field);
        String name = owner.getSimpleName() + "." + field.getName();
        forEachColumn(attribute, column -> compare(name, fieldNullable, column, columnNullable, mismatches, unmapped));
    }

    private static void forEachColumn(ModelPart part, Consumer<String> action) {
        part.forEachSelectable((index, selectable) -> {
            if (!selectable.isFormula()) {
                action.accept(key(selectable.getContainingTableExpression(), selectable.getSelectionExpression()));
            }
        });
    }

    private static void compare(String field, boolean fieldNullable, String column, Map<String, Boolean> columnNullable,
            List<String> mismatches, List<String> unmapped) {
        Boolean nullable = columnNullable.get(column);
        if (nullable == null) {
            unmapped.add(field + " -> " + column);
        } else if (nullable != fieldNullable) {
            mismatches.add(field + " is " + (fieldNullable ? "@Nullable" : "non-null") + " but " + column + " is "
                    + (nullable ? "NULL" : "NOT NULL"));
        }
    }

    private static boolean isNullable(Field field) {
        return !field.getType().isPrimitive() && field.getAnnotatedType().isAnnotationPresent(Nullable.class);
    }

    private static Field declaredField(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        throw new IllegalStateException(owner.getName() + " declares no field " + name);
    }

    private Map<String, Boolean> columnNullability() {
        Map<String, Boolean> nullability = new HashMap<>();
        new JdbcTemplate(dataSource).query(
                "SELECT table_name, column_name, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE()",
                row -> {
                    nullability.put(key(row.getString(1), row.getString(2)),
                            NULLABLE_COLUMN.equals(row.getString(3)));
                });
        return nullability;
    }

    private static String key(String table, String column) {
        return (unquote(table) + "." + unquote(column)).toLowerCase(Locale.ROOT);
    }

    private static String unquote(String identifier) {
        return identifier.replace("`", "").replace("\"", "");
    }
}
