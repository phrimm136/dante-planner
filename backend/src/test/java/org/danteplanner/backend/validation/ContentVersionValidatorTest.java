package org.danteplanner.backend.validation;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.TestDataFactory;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
        void requireCreatableVersion_WhenMdCurrentVersion_Succeeds() {
            assertThatCode(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, 8))
                    .doesNotThrowAnyException();
        }

        @Test
        void requireCreatableVersion_WhenMdOldVersion_Throws() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, 5))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void requireCreatableVersion_WhenMdFutureVersion_Throws() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, 99))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void requireCreatableVersion_WhenRrVersion1_Succeeds() {
            assertThatCode(() -> validator.requireCreatableVersion(PlannerType.REFRACTED_RAILWAY, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        void requireCreatableVersion_WhenRrVersion5_Succeeds() {
            assertThatCode(() -> validator.requireCreatableVersion(PlannerType.REFRACTED_RAILWAY, 5))
                    .doesNotThrowAnyException();
        }

        @Test
        void requireCreatableVersion_WhenRrVersionNotInList_Throws() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.REFRACTED_RAILWAY, 3))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void requireCreatableVersion_WhenAccepted_CarriesTheTypeAndTheVersion() {
            var version = validator.requireCreatableVersion(PlannerType.REFRACTED_RAILWAY, 5);

            assertThat(version.plannerType()).isEqualTo(PlannerType.REFRACTED_RAILWAY);
            assertThat(version.value()).isEqualTo(5);
        }

        @Test
        void requireCreatableVersion_WhenRefused_LogsTheRefusal() {
            try (LogCapture capture = LogCapture.onThisThread(ContentVersionValidator.class)) {
                assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, null))
                        .withViolation("CONTENT_VERSION_REQUIRED");
                assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, 5))
                        .withViolation("INVALID_CONTENT_VERSION");
                assertThat(capture.at(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Validation failed: content version is null",
                                "Validation failed: Mirror Dungeon create version 5 not in [8]");
            }
        }

        @Test
        void requireCreatableVersion_WhenNullVersion_Throws() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, null))
                    .withViolation("CONTENT_VERSION_REQUIRED")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("CONTENT_VERSION_REQUIRED"));
        }
    }

    @Nested
    class PlannerVersionsFileTests {

        @Test
        void requireCreatableVersion_WhenMdVersionIsAnEarlierListedSeason_ThrowsInvalidContentVersion() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.MIRROR_DUNGEON, 6))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }

        @Test
        void requireCreatableVersion_WhenRrVersionIsNotListed_ThrowsInvalidContentVersion() {
            assertRejected(() -> validator.requireCreatableVersion(PlannerType.REFRACTED_RAILWAY, 2))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            ex -> assertThat(ex.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
        }
    }

}
