package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlannerPublishValidatorTest {

    private final PlannerPublishValidator validator = new PlannerPublishValidator();

    @Test
    void requireKnownKeywords_WhenContentSelectsAnUnknownKeyword_ThrowsUnknownKeyword() {
        PlannerValidationException ex = catchThrowableOfType(PlannerValidationException.class,
                () -> validator.requireKnownKeywords("{\"selectedKeywords\":[\"Sinking\",\"NotAKeyword\"]}"));

        assertThat(ex).isNotNull();
        assertThat(ex.getOriginalCode()).isEqualTo("UNKNOWN_KEYWORD");
        assertThat(ex.getBody().getProperties()).containsEntry("code", "UNKNOWN_KEYWORD");
        assertThat(ex.getMessage()).isEqualTo("Unknown keywords: [NotAKeyword]");
    }

    @Test
    void requireKnownKeywords_WhenAnElementIsNotAString_ThrowsUnknownKeyword() {
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
