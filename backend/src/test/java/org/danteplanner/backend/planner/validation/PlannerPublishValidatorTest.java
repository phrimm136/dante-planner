package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlannerPublishValidatorTest {

    private final PlannerPublishValidator validator = new PlannerPublishValidator();

    @Test
    void requireKnownKeywords_WhenContentSelectsAnUnknownKeyword_ThrowsKeywordInvalid() {
        PlannerValidationException ex = catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireKnownKeywords("{\"selectedKeywords\":[\"Sinking\",\"NotAKeyword\"]}"));

        assertThat(ex).isNotNull();
        assertThat(ex.getOriginalCode()).isEqualTo("KEYWORD_INVALID");
        assertThat(ex.getBody().getProperties()).containsEntry("code", "KEYWORD_INVALID");
        assertThat(ex.getMessage()).isEqualTo("Unknown keywords: [NotAKeyword]");
    }

    @Test
    void requireKnownKeywords_WhenAnElementIsNotAString_ThrowsKeywordInvalid() {
        assertThat(catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireKnownKeywords("{\"selectedKeywords\":[9828]}")))
                .isNotNull();
    }

    @Test
    void requireKnownKeywords_WhenContentSelectsKnownAndRenamedKeywords_Passes() {
        assertThatCode(() -> validator.requireKnownKeywords(
                "{\"selectedKeywords\":[\"Sinking\",\"AccelBullet\",\"ChargeLoad\"]}"))
                .doesNotThrowAnyException();
    }
}
