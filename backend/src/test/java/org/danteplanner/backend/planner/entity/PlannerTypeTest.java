package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTypeTest {

    @Test
    void isValidCategory_WhenValidMdCategory_ReturnsTrue() {
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("5F"));
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("10F"));
        assertTrue(PlannerType.MIRROR_DUNGEON.isValidCategory("15F"));
    }

    @Test
    void isValidCategory_WhenValidRrCategory_ReturnsTrue() {
        assertTrue(PlannerType.REFRACTED_RAILWAY.isValidCategory("RR_PLACEHOLDER"));
    }

    @Test
    void isValidCategory_WhenInvalidCategory_ReturnsFalse() {
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory("INVALID"));
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory(""));
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory(null));
    }

    @Test
    void isValidCategory_WhenMdCategoryWithRrType_ReturnsFalse() {
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("5F"));
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("10F"));
        assertFalse(PlannerType.REFRACTED_RAILWAY.isValidCategory("15F"));
    }

    @Test
    void isValidCategory_WhenRrCategoryWithMdType_ReturnsFalse() {
        assertFalse(PlannerType.MIRROR_DUNGEON.isValidCategory("RR_PLACEHOLDER"));
    }
}
