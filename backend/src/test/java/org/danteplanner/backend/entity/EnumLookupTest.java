package org.danteplanner.backend.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.api.UserRole;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the shared {@link EnumLookup} resolver.
 */
class EnumLookupTest {

    @Nested
    class FromValueTests {

        @Test
        void fromValue_WhenMdCategory_ReturnsConstant() {
            assertThat(EnumLookup.fromValue(MDCategory.class, "5F")).isEqualTo(MDCategory.F5);
            assertThat(EnumLookup.fromValue(MDCategory.class, "10F")).isEqualTo(MDCategory.F10);
            assertThat(EnumLookup.fromValue(MDCategory.class, "15F")).isEqualTo(MDCategory.F15);
        }

        @Test
        void fromValue_WhenPlannerType_ReturnsConstant() {
            assertThat(EnumLookup.fromValue(PlannerType.class, "MIRROR_DUNGEON")).isEqualTo(PlannerType.MIRROR_DUNGEON);
            assertThat(EnumLookup.fromValue(PlannerType.class, "REFRACTED_RAILWAY"))
                    .isEqualTo(PlannerType.REFRACTED_RAILWAY);
        }

        @Test
        void fromValue_WhenUnknown_ThrowsWithTheOriginalMessageFormat() {
            assertThatThrownBy(() -> EnumLookup.fromValue(MDCategory.class, "banana"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown MDCategory value: banana");
        }

        @Test
        void fromValue_WhenNull_Throws() {
            assertThatThrownBy(() -> EnumLookup.fromValue(MDCategory.class, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown MDCategory value: null");
        }
    }

    @Nested
    class IsValidTests {

        @Test
        void isValid_WhenMdCategory_True() {
            assertThat(EnumLookup.isValid(MDCategory.class, "5F")).isTrue();
            assertThat(EnumLookup.isValid(MDCategory.class, "10F")).isTrue();
            assertThat(EnumLookup.isValid(MDCategory.class, "15F")).isTrue();
        }

        @Test
        void isValid_WhenUnknownOrNull_False() {
            assertThat(EnumLookup.isValid(MDCategory.class, "banana")).isFalse();
            assertThat(EnumLookup.isValid(MDCategory.class, null)).isFalse();
        }

        @Test
        void isValid_WhenUserRole_True() {
            assertThat(EnumLookup.isValid(UserRole.class, "ADMIN")).isTrue();
            assertThat(EnumLookup.isValid(UserRole.class, "SUPERADMIN")).isFalse();
        }
    }
}
