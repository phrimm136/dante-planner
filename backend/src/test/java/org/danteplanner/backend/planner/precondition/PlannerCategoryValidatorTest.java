package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlannerCategoryValidatorTest {

    private final PlannerCategoryValidator validator = new PlannerCategoryValidator();

    @Test
    void requireCategoryForType_WhenTheCategoryBelongsToAnotherType_ThrowsMismatchedCategory() {
        PlannerValidationException ex = catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireCategoryForType(PlannerType.MIRROR_DUNGEON, "RR_PLACEHOLDER"));

        assertThat(ex).isNotNull();
        assertThat(ex.getOriginalCode()).isEqualTo("MISMATCHED_CATEGORY");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"XX", "20F", "5f"})
    void requireCategoryForType_WhenAMirrorDungeonCategoryIsUnknown_ThrowsMismatchedCategory(String category) {
        PlannerValidationException ex = catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireCategoryForType(PlannerType.MIRROR_DUNGEON, category));

        assertThat(ex).isNotNull();
        assertThat(ex.getOriginalCode()).isEqualTo("MISMATCHED_CATEGORY");
    }

    @Test
    void requireCategoryForType_WhenTheTypeAcceptsACategoryTheContentParseCannotUse_ThrowsInvalidCategoryAlone() {
        PlannerValidationException ex = catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireCategoryForType(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER"));

        assertThat(ex).isNotNull();
        assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CATEGORY");
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("INVALID_CATEGORY", "", "Invalid category: RR_PLACEHOLDER"));
    }

    @ParameterizedTest
    @EnumSource(MDCategory.class)
    void requireCategoryForType_WhenTheCategoryBelongsToTheType_ReturnsIt(MDCategory category) {
        assertThat(validator.requireCategoryForType(PlannerType.MIRROR_DUNGEON, category.getValue()))
                .isEqualTo(category);
    }
}
