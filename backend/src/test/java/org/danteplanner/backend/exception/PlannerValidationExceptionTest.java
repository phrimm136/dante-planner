package org.danteplanner.backend.exception;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PlannerValidationExceptionTest {

    private static final String CONTENT = "{\"equipment\":[]}";

    @Test
    @DisplayName("Single-error constructor should produce empty subErrors list")
    void singleErrorConstructor_WhenUsed_HasEmptySubErrors() {
        PlannerValidationException ex = new PlannerValidationException("MY_CODE", "My message");

        assertEquals("MY_CODE", ex.getOriginalCode());
        assertEquals("My message", ex.getMessage());
        assertTrue(ex.getSubErrors().isEmpty());
    }

    @Test
    @DisplayName("rejecting() carries every violation as a sub-error with its rendered path")
    void rejecting_WhenTwoViolations_CarriesBothAsSubErrorsWithPaths() {
        ContentVerdict.Rejected rejected = new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.MISSING_SINNER, ContentPath.of("equipment"), "First error"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("equipment").key("05"), "Second error")));

        PlannerValidationException ex = PlannerValidationException.rejecting(rejected, CONTENT);

        assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("MISSING_SINNER", "equipment", "First error"),
                new ValidationError("INVALID_FIELD_TYPE", "equipment[05]", "Second error"));
        assertEquals("[MISSING_SINNER] First error; [INVALID_FIELD_TYPE] Second error", ex.getMessage());
        assertEquals(CONTENT, ex.getFailedContent());
    }

    @Test
    @DisplayName("rejecting() with one violation still reports it as a sub-error under the generic code")
    void rejecting_WhenOneViolation_HasExactlyOneSubErrorUnderTheGenericCode() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.UNKNOWN_EGO, ContentPath.of("equipment"), "Only message"))), CONTENT);

        assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_EGO", "equipment", "Only message"));
    }

    @Test
    @DisplayName("refusing() keeps the violation's own code and message with no sub-errors")
    void refusing_WhenAUserFacingViolation_KeepsItsCodeAndMessage() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.EMPTY_CONTENT, ContentPath.root(), "Content is empty or null")), CONTENT);

        assertEquals("EMPTY_CONTENT", ex.getOriginalCode());
        assertEquals("Content is empty or null", ex.getMessage());
        assertEquals("EMPTY_CONTENT", ex.getBody().getProperties().get("code"));
        assertTrue(ex.getSubErrors().isEmpty());
        assertEquals(CONTENT, ex.getFailedContent());
    }

    @Test
    @DisplayName("refusing() masks a code that is not user-facing to the generic code on the wire")
    void refusing_WhenACodeIsNotUserFacing_MasksItOnTheWire() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.UNKNOWN_FIELD, ContentPath.root(), "Unknown fields: [theme]")), CONTENT);

        assertEquals("UNKNOWN_FIELD", ex.getOriginalCode());
        assertEquals("VALIDATION_ERROR", ex.getBody().getProperties().get("code"));
        assertEquals("Invalid planner content structure", ex.getBody().getDetail());
    }
}
