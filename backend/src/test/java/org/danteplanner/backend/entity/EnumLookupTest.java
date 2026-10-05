package org.danteplanner.backend.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.api.UserRole;

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
    class FromValueTests {

        @Test
        void fromValue_WhenMdCategory_ReturnsConstant() {
            assertEquals(MDCategory.F5, EnumLookup.fromValue(MDCategory.class, "5F"));
            assertEquals(MDCategory.F10, EnumLookup.fromValue(MDCategory.class, "10F"));
            assertEquals(MDCategory.F15, EnumLookup.fromValue(MDCategory.class, "15F"));
        }

        @Test
        void fromValue_WhenPlannerType_ReturnsConstant() {
            assertEquals(PlannerType.MIRROR_DUNGEON, EnumLookup.fromValue(PlannerType.class, "MIRROR_DUNGEON"));
            assertEquals(PlannerType.REFRACTED_RAILWAY, EnumLookup.fromValue(PlannerType.class, "REFRACTED_RAILWAY"));
        }

        @Test
        void fromValue_WhenUnknown_ThrowsWithTheOriginalMessageFormat() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> EnumLookup.fromValue(MDCategory.class, "banana"));
            assertEquals("Unknown MDCategory value: banana", ex.getMessage());
        }

        @Test
        void fromValue_WhenNull_Throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> EnumLookup.fromValue(MDCategory.class, null));
        }
    }

    @Nested
    class IsValidTests {

        @Test
        void isValid_WhenMdCategory_True() {
            assertTrue(EnumLookup.isValid(MDCategory.class, "5F"));
            assertTrue(EnumLookup.isValid(MDCategory.class, "10F"));
            assertTrue(EnumLookup.isValid(MDCategory.class, "15F"));
        }

        @Test
        void isValid_WhenUnknownOrNull_False() {
            assertFalse(EnumLookup.isValid(MDCategory.class, "banana"));
            assertFalse(EnumLookup.isValid(MDCategory.class, null));
        }

        @Test
        void isValid_WhenUserRole_True() {
            assertTrue(EnumLookup.isValid(UserRole.class, "ADMIN"));
            assertFalse(EnumLookup.isValid(UserRole.class, "SUPERADMIN"));
        }
    }
}
