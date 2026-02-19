package org.danteplanner.backend.validation;

import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentVersionValidator.
 */
class ContentVersionValidatorTest {

    private ContentVersionValidator validator;

    private static final int MD_CURRENT_VERSION = 7;
    private static final String MD_AVAILABLE_VERSIONS_RAW = "6,7";
    private static final String RR_AVAILABLE_VERSIONS_RAW = "1,5";

    @BeforeEach
    void setUp() {
        validator = new ContentVersionValidator(
                MD_CURRENT_VERSION,
                MD_AVAILABLE_VERSIONS_RAW,
                RR_AVAILABLE_VERSIONS_RAW
        );
    }

    @Nested
    @DisplayName("validateVersionForCreate Tests")
    class ValidateVersionForCreateTests {

        @Test
        @DisplayName("MD: accepts current version")
        void mdCurrentVersion_succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 7));
        }

        @Test
        @DisplayName("MD: rejects old version")
        void mdOldVersion_throwsException() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 5)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getErrorCode());
        }

        @Test
        @DisplayName("MD: rejects future version")
        void mdFutureVersion_throwsException() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, 99)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getErrorCode());
        }

        @Test
        @DisplayName("RR: accepts version 1")
        void rrVersion1_succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 1));
        }

        @Test
        @DisplayName("RR: accepts version 5")
        void rrVersion5_succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 5));
        }

        @Test
        @DisplayName("RR: rejects version not in list")
        void rrVersionNotInList_throwsException() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.REFRACTED_RAILWAY, 3)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getErrorCode());
        }

        @Test
        @DisplayName("Null version throws exception")
        void nullVersion_throwsException() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForCreate(PlannerType.MIRROR_DUNGEON, null)
            );
            assertEquals("CONTENT_VERSION_REQUIRED", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Constructor Edge Cases")
    class ConstructorTests {

        @Test
        @DisplayName("Should fail fast with clear error for non-numeric versions")
        void constructor_InvalidVersionFormat_ThrowsIllegalArgumentException() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ContentVersionValidator(6, "6,seven,8", "1,5")
            );
            assertTrue(ex.getMessage().contains("Invalid version list format"));
        }
    }

    @Nested
    @DisplayName("validateVersionForUpdate Tests")
    class ValidateVersionForUpdateTests {

        @Test
        @DisplayName("MD: accepts version in available list")
        void mdVersionInList_succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForUpdate(PlannerType.MIRROR_DUNGEON, 6));
        }

        @Test
        @DisplayName("MD: rejects version not in available list")
        void mdVersionNotInList_throwsException() {
            PlannerValidationException ex = assertThrows(
                    PlannerValidationException.class,
                    () -> validator.validateVersionForUpdate(PlannerType.MIRROR_DUNGEON, 5)
            );
            assertEquals("INVALID_CONTENT_VERSION", ex.getErrorCode());
        }

        @Test
        @DisplayName("RR: accepts version in list")
        void rrVersionInList_succeeds() {
            assertDoesNotThrow(() -> validator.validateVersionForUpdate(PlannerType.REFRACTED_RAILWAY, 5));
        }
    }
}
