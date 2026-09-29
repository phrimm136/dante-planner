package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void requireCategoryForType_WhenTheCategoryBelongsToTheType_Passes() {
        assertThatCode(() -> validator.requireCategoryForType(PlannerType.MIRROR_DUNGEON, "5F"))
                .doesNotThrowAnyException();
    }
}
