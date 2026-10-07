package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTypeTest {

    @Test
    void isValidCategory_WhenValidMdCategory_ReturnsTrue() {
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("5F")).isTrue();
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("10F")).isTrue();
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("15F")).isTrue();
    }

    @Test
    void isValidCategory_WhenValidRrCategory_ReturnsTrue() {
        assertThat(PlannerType.REFRACTED_RAILWAY.isValidCategory("RR_PLACEHOLDER")).isTrue();
    }

    @Test
    void isValidCategory_WhenInvalidCategory_ReturnsFalse() {
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("INVALID")).isFalse();
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("")).isFalse();
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory(null)).isFalse();
    }

    @Test
    void isValidCategory_WhenMdCategoryWithRrType_ReturnsFalse() {
        assertThat(PlannerType.REFRACTED_RAILWAY.isValidCategory("5F")).isFalse();
        assertThat(PlannerType.REFRACTED_RAILWAY.isValidCategory("10F")).isFalse();
        assertThat(PlannerType.REFRACTED_RAILWAY.isValidCategory("15F")).isFalse();
    }

    @Test
    void isValidCategory_WhenRrCategoryWithMdType_ReturnsFalse() {
        assertThat(PlannerType.MIRROR_DUNGEON.isValidCategory("RR_PLACEHOLDER")).isFalse();
    }
}
