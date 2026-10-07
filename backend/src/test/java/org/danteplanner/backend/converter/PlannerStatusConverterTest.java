package org.danteplanner.backend.converter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PlannerStatusConverter.
 *
 * <p>Verifies the converter maps {@link PlannerStatus} to the lowercase
 * {@code draft}/{@code saved} values expected by the MySQL ENUM column.</p>
 */
class PlannerStatusConverterTest {

    private PlannerStatusConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PlannerStatusConverter();
    }

    @Nested
    class ToDatabaseColumn {

        @Test
        void convertToDatabaseColumn_WhenDraft_ReturnsLowercaseDraft() {
            assertThat(converter.convertToDatabaseColumn(PlannerStatus.DRAFT)).isEqualTo("draft");
        }

        @Test
        void convertToDatabaseColumn_WhenSaved_ReturnsLowercaseSaved() {
            assertThat(converter.convertToDatabaseColumn(PlannerStatus.SAVED)).isEqualTo("saved");
        }

        @Test
        void convertToDatabaseColumn_WhenNull_ReturnsNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenDraft_ReturnsDraftEnum() {
            assertThat(converter.convertToEntityAttribute("draft")).isEqualTo(PlannerStatus.DRAFT);
        }

        @Test
        void convertToEntityAttribute_WhenSaved_ReturnsSavedEnum() {
            assertThat(converter.convertToEntityAttribute("saved")).isEqualTo(PlannerStatus.SAVED);
        }

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }
}
