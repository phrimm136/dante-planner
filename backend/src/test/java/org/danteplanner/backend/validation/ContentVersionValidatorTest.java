package org.danteplanner.backend.validation;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.support.TestDataFactory;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for ContentVersionValidator.
 */
class ContentVersionValidatorTest {

    private ContentVersionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContentVersionValidator(TestDataFactory.gameDataRegistry());
    }

    @Nested
    class ValidateVersionForCreateTests {

        @Test
        void validateVersionForCreate_WhenMdCurrentVersion_Succeeds() {
            assertThatCode(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 8))
                    .doesNotThrowAnyException();
        }

        @Test
        void validateVersionForCreate_WhenMdOldVersion_Throws() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 5))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void validateVersionForCreate_WhenMdFutureVersion_Throws() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 99))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void validateVersionForCreate_WhenRrVersion1_Succeeds() {
            assertThatCode(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        void validateVersionForCreate_WhenRrVersion5_Succeeds() {
            assertThatCode(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 5))
                    .doesNotThrowAnyException();
        }

        @Test
        void validateVersionForCreate_WhenRrVersionNotInList_Throws() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 3))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void validateVersionForCreate_WhenNullVersion_Throws() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, null))
                    .withViolation("CONTENT_VERSION_REQUIRED")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("CONTENT_VERSION_REQUIRED"));
        }
    }

    @Nested
    class PlannerVersionsFileTests {

        @Test
        void validateVersionForCreate_WhenMdVersionIsAnEarlierListedSeason_ThrowsInvalidContentVersion() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 6))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void validateVersionForCreate_WhenRrVersionIsNotListed_ThrowsInvalidContentVersion() {
            assertRejected(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 2))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }
    }

}
