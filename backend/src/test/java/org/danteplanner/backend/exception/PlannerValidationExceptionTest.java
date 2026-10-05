package org.danteplanner.backend.exception;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PlannerValidationExceptionTest {

    private static final String CONTENT = "{\"equipment\":[]}";

    @Test
    void singleErrorConstructor_WhenUsed_HasEmptySubErrors() {
        PlannerValidationException ex = new PlannerValidationException("MY_CODE", "My message");

        assertEquals("MY_CODE", ex.getOriginalCode());
        assertEquals("My message", ex.getMessage());
        assertTrue(ex.getSubErrors().isEmpty());
    }

    @Test
    void rejecting_WhenTwoViolations_CarriesBothAsSubErrorsWithPaths() {
        ContentVerdict.Rejected rejected = new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.MISSING_REQUIRED_FIELD, ContentPath.of("equipment"), "First error"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("equipment").key("05"), "Second error")));

        PlannerValidationException ex = PlannerValidationException.rejecting(rejected, CONTENT);

        assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("MISSING_REQUIRED_FIELD", "equipment", "First error"),
                new ValidationError("INVALID_FIELD_TYPE", "equipment[05]", "Second error"));
        assertEquals("[MISSING_REQUIRED_FIELD] First error; [INVALID_FIELD_TYPE] Second error", ex.getMessage());
        assertEquals(CONTENT, ex.getFailedContent());
    }

    @Test
    void rejecting_WhenOneViolation_HasExactlyOneSubErrorUnderTheGenericCode() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.UNKNOWN_EGO, ContentPath.of("equipment"), "Only message"))), CONTENT);

        assertEquals("VALIDATION_ERROR", ex.getOriginalCode());
        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_EGO", "equipment", "Only message"));
    }

    @Test
    void refusing_WhenAUserFacingViolation_KeepsItsCodeAndMessage() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.EMPTY_CONTENT, ContentPath.root(), "Content is empty or null")), CONTENT);

        assertEquals("EMPTY_CONTENT", ex.getOriginalCode());
        assertEquals("Content is empty or null", ex.getMessage());
        assertEquals("EMPTY_CONTENT", ex.getBody().getProperties().get("code"));
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("EMPTY_CONTENT", "", "Content is empty or null"));
        assertEquals(CONTENT, ex.getFailedContent());
    }

    @Test
    void refusing_WhenACodeIsNotUserFacing_MasksItOnTheWire() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.UNKNOWN_FIELD, ContentPath.root(), "Unknown fields: [theme]")), CONTENT);

        assertEquals("UNKNOWN_FIELD", ex.getOriginalCode());
        assertEquals("VALIDATION_ERROR", ex.getBody().getProperties().get("code"));
        assertEquals("Invalid planner content structure", ex.getBody().getDetail());
    }

    @Test
    void refusing_WhenANoteIsOversized_CarriesTheNotePathAndKeepsTheBody() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.SIZE_EXCEEDED, ContentPath.of("sectionNotes").key("floor1"),
                        "Note 'floor1' size 1100 exceeds limit 1024")), CONTENT);

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("SIZE_EXCEEDED", "sectionNotes[floor1]", "Note 'floor1' size 1100 exceeds limit 1024"));
        assertEquals(400, ex.getBody().getStatus());
        assertEquals(java.util.Map.of("code", "SIZE_EXCEEDED", "message", "Note 'floor1' size 1100 exceeds limit 1024"),
                ex.getBody().getProperties());
        assertEquals("Note 'floor1' size 1100 exceeds limit 1024", ex.getBody().getDetail());
    }

    @Test
    void rejecting_WhenEveryViolationIsAnUnknownKeyword_AnswersUnknownKeywordOnTheWireListingThem() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD, ContentPath.of("selectedKeywords").index(1),
                        "selectedKeywords", "Zilch"),
                Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD, ContentPath.of("selectedKeywords").index(2),
                        "selectedKeywords", "Nope"))),
                "{\"selectedKeywords\":[\"Sinking\",\"Zilch\",\"Nope\",\"AccelBullet\"]}");

        assertEquals("UNKNOWN_KEYWORD", ex.getOriginalCode());
        assertEquals("UNKNOWN_KEYWORD", ex.getBody().getProperties().get("code"));
        assertEquals("Unknown keywords: [Nope, Zilch]", ex.getBody().getDetail());
        assertFalse(ex.reportable());
        assertThat(ex.getSubErrors()).extracting(ValidationError::path)
                .containsExactly("selectedKeywords[1]", "selectedKeywords[2]");
    }

    @Test
    void rejecting_WhenAnUnknownKeywordSitsBesideAnotherViolation_MasksToTheGenericCode() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.UNKNOWN_IDENTITY, ContentPath.of("equipment").key("01"), "identity"),
                Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD, ContentPath.of("selectedKeywords").index(0),
                        "selectedKeywords", "Nope"))),
                "{\"selectedKeywords\":[\"Nope\"]}");

        assertEquals("VALIDATION_ERROR", ex.getBody().getProperties().get("code"));
        assertEquals("Invalid planner content structure", ex.getBody().getDetail());
    }
}
