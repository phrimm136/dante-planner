package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlannerType category rule")
class PlannerTypeTest {

    @Test
    @DisplayName("Should return true for valid MD category with MIRROR_DUNGEON type")
    void isValidCategory_WhenValidMdCategory_ReturnsTrue() {
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("5F"));
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("10F"));
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("15F"));
    }

    @Test
    @DisplayName("Should return true for valid RR category with REFRACTED_RAILWAY type")
    void isValidCategory_WhenValidRrCategory_ReturnsTrue() {
        assertTrue(PlannerType.REFRACTED_RAILWAY.isValidCategory("RR_PLACEHOLDER"));
    }

    @Test
    @DisplayName("Should return false for invalid category")
    void isValidCategory_WhenInvalidCategory_ReturnsFalse() {
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory("INVALID"));
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory(""));
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory(null));
    }

    @Test
    @DisplayName("Should return false when MD category used with RR type")
    void isValidCategory_WhenMdCategoryWithRrType_ReturnsFalse() {
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("5F"));
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("10F"));
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("15F"));
    }

    @Test
    @DisplayName("Should return false when RR category used with MD type")
    void isValidCategory_WhenRrCategoryWithMdType_ReturnsFalse() {
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory("RR_PLACEHOLDER"));
    }
}
