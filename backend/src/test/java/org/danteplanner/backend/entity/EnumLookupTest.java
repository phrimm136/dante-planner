package org.danteplanner.backend.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.UserRole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shared {@link EnumLookup} resolver.
 */
class EnumLookupTest {

    @Nested
    @DisplayName("fromValue Tests")
    class FromValueTests {

        @Test
        @DisplayName("returns the matching MDCategory constant")
        void fromValue_mdCategory_returnsConstant() {
            assertEquals(MDCategory.F5, EnumLookup.fromValue(MDCategory.class, "5F"));
            assertEquals(MDCategory.F10, EnumLookup.fromValue(MDCategory.class, "10F"));
            assertEquals(MDCategory.F15, EnumLookup.fromValue(MDCategory.class, "15F"));
        }

        @Test
        @DisplayName("returns the matching PlannerType constant")
        void fromValue_plannerType_returnsConstant() {
            assertEquals(PlannerType.MIRROR_DUNGEON, EnumLookup.fromValue(PlannerType.class, "MIRROR_DUNGEON"));
            assertEquals(PlannerType.REFRACTED_RAILWAY, EnumLookup.fromValue(PlannerType.class, "REFRACTED_RAILWAY"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException on unknown value with the original message format")
        void fromValue_unknown_throws() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> EnumLookup.fromValue(MDCategory.class, "banana"));
            assertEquals("Unknown MDCategory value: banana", ex.getMessage());
        }

        @Test
        @DisplayName("throws IllegalArgumentException on null value")
        void fromValue_null_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> EnumLookup.fromValue(MDCategory.class, null));
        }
    }

    @Nested
    @DisplayName("isValid Tests")
    class IsValidTests {

        @Test
        @DisplayName("returns true for valid MDCategory values")
        void isValid_mdCategory_true() {
            assertTrue(EnumLookup.isValid(MDCategory.class, "5F"));
            assertTrue(EnumLookup.isValid(MDCategory.class, "10F"));
            assertTrue(EnumLookup.isValid(MDCategory.class, "15F"));
        }

        @Test
        @DisplayName("returns false for unknown and null values without throwing")
        void isValid_unknownOrNull_false() {
            assertFalse(EnumLookup.isValid(MDCategory.class, "banana"));
            assertFalse(EnumLookup.isValid(MDCategory.class, null));
        }

        @Test
        @DisplayName("returns true for a valid UserRole value")
        void isValid_userRole_true() {
            assertTrue(EnumLookup.isValid(UserRole.class, "ADMIN"));
            assertFalse(EnumLookup.isValid(UserRole.class, "SUPERADMIN"));
        }
    }
}
