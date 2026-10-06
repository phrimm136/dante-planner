package org.danteplanner.backend.validation;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.support.TestDataFactory;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 7));
        }

        @Test
        void validateVersionForCreate_WhenMdOldVersion_Throws() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 5)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getOriginalCode());
        }

        @Test
        void validateVersionForCreate_WhenMdFutureVersion_Throws() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 99)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getOriginalCode());
        }

        @Test
        void validateVersionForCreate_WhenRrVersion1_Succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 1));
        }

        @Test
        void validateVersionForCreate_WhenRrVersion5_Succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 5));
        }

        @Test
        void validateVersionForCreate_WhenRrVersionNotInList_Throws() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 3)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getOriginalCode());
        }

        @Test
        void validateVersionForCreate_WhenNullVersion_Throws() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, null)
            );
            assertEquals("CONTENT_VERSION_REQUIRED", ex.getOriginalCode());
        }
    }

    @Nested
    class PlannerVersionsFileTests {

        @Test
        void validateVersionForCreate_WhenMdVersionIsAnEarlierListedSeason_ThrowsInvalidContentVersion() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 6)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getOriginalCode());
        }

        @Test
        void validateVersionForCreate_WhenRrVersionIsNotListed_ThrowsInvalidContentVersion() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 2)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getOriginalCode());
        }
    }

}
