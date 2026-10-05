package org.danteplanner.backend.converter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
            assertEquals("draft", converter.convertToDatabaseColumn(PlannerStatus.DRAFT));
        }

        @Test
        void convertToDatabaseColumn_WhenSaved_ReturnsLowercaseSaved() {
            assertEquals("saved", converter.convertToDatabaseColumn(PlannerStatus.SAVED));
        }

        @Test
        void convertToDatabaseColumn_WhenNull_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenDraft_ReturnsDraftEnum() {
            assertEquals(PlannerStatus.DRAFT, converter.convertToEntityAttribute("draft"));
        }

        @Test
        void convertToEntityAttribute_WhenSaved_ReturnsSavedEnum() {
            assertEquals(PlannerStatus.SAVED, converter.convertToEntityAttribute("saved"));
        }

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsNull() {
            assertNull(converter.convertToEntityAttribute(null));
        }
    }
}
