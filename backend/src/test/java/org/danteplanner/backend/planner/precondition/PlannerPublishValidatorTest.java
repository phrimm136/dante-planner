package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class PlannerPublishValidatorTest {

    private final PlannerPublishValidator validator = new PlannerPublishValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void requireTitle_WhenTheTitleIsAbsentOrBlank_RefusesWithMissingTitleAtTheTitle(String title) {
        assertRejected(() -> validator.requireTitle(title))
                .withCode("VALIDATION_ERROR")
                .withViolation("MISSING_TITLE", "title")
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getOriginalCode()).isEqualTo("MISSING_TITLE");
                    assertThat(ex.getSubErrors()).containsExactly(
                            new ValidationError("MISSING_TITLE", "title", "Title is required for publishing"));
                    assertThat(ex.getBody().getDetail()).isEqualTo("Invalid planner content structure");
                });
    }

    @Test
    void requireTitle_WhenTheTitleIsPresent_Passes() {
        assertThatCode(() -> validator.requireTitle("Corpus")).doesNotThrowAnyException();
    }
}
