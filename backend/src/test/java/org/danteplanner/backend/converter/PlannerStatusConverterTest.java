package org.danteplanner.backend.converter;

import org.danteplanner.backend.entity.PlannerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("convertToDatabaseColumn")
    class ToDatabaseColumn {

        @Test
        @DisplayName("DRAFT maps to lowercase 'draft'")
        void draftMapsToLowercase() {
            assertEquals("draft", converter.convertToDatabaseColumn(PlannerStatus.DRAFT));
        }

        @Test
        @DisplayName("SAVED maps to lowercase 'saved'")
        void savedMapsToLowercase() {
            assertEquals("saved", converter.convertToDatabaseColumn(PlannerStatus.SAVED));
        }

        @Test
        @DisplayName("null maps to null")
        void nullMapsToNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ToEntityAttribute {

        @Test
        @DisplayName("'draft' maps to DRAFT")
        void draftStringMapsToEnum() {
            assertEquals(PlannerStatus.DRAFT, converter.convertToEntityAttribute("draft"));
        }

        @Test
        @DisplayName("'saved' maps to SAVED")
        void savedStringMapsToEnum() {
            assertEquals(PlannerStatus.SAVED, converter.convertToEntityAttribute("saved"));
        }

        @Test
        @DisplayName("null maps to null")
        void nullMapsToNull() {
            assertNull(converter.convertToEntityAttribute(null));
        }
    }
}
