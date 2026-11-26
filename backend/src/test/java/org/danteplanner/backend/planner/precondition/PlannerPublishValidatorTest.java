package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.entity.Publication.Unpublished;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator.PublishTitle;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class PlannerPublishValidatorTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final PlannerPublishValidator validator = new PlannerPublishValidator();

    private static TestDataFactory.PlannerBuilder planner() {
        return TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void parse_WhenTheTitleIsAbsentOrBlank_RefusesWithMissingTitleAtTheTitle(String title) {
        assertRejected(() -> PublishTitle.parse(title).orElseThrow(PlannerValidationException::refusing))
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
    void parse_WhenTheTitleIsPresent_HoldsIt() {
        PublishTitle title = PublishTitle.parse("Corpus").orElseThrow(PlannerValidationException::refusing);

        assertThat(title.value()).isEqualTo("Corpus");
    }

    @Test
    void requirePublishable_WhenTakenDown_Throws() {
        StoredPlanner planner = planner().takenDownAt(EARLIER).build();

        assertRejected(() -> validator.requirePublishable(planner))
                .withCode("PLANNER_FORBIDDEN")
                .satisfies(rejection -> assertThat(rejection.getMessage())
                        .isEqualTo("User is not authorized to modify planner with id: " + planner.id()));
    }

    @Test
    void requirePublishable_WhenNotTakenDown_HoldsThePublicationAndTitle() {
        PlannerPublishValidator.Publishable publishable =
                validator.requirePublishable(planner().title("Corpus").firstPublishedAt(EARLIER).build());

        assertThat(publishable.publication()).isEqualTo(new Unpublished(EARLIER));
        assertThat(publishable.title().value()).isEqualTo("Corpus");
    }

    @Test
    void requirePublishable_WhenTakenDownWithABlankTitle_RefusesTheTitleFirst() {
        assertRejected(() -> validator.requirePublishable(planner().title(" ").takenDownAt(EARLIER).build()))
                .withCode("VALIDATION_ERROR")
                .withViolation("MISSING_TITLE", "title");
    }
}
