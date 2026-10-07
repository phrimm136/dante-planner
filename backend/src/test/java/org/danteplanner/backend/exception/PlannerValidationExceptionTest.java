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

class PlannerValidationExceptionTest {

    private static final String CONTENT = "{\"equipment\":[]}";

    @Test
    void singleErrorConstructor_WhenUsed_HasEmptySubErrors() {
        PlannerValidationException ex = new PlannerValidationException("MY_CODE", "My message");

        assertThat(ex.getOriginalCode()).isEqualTo("MY_CODE");
        assertThat(ex.getMessage()).isEqualTo("My message");
        assertThat(ex.getSubErrors()).isEmpty();
    }

    @Test
    void rejecting_WhenTwoViolations_CarriesBothAsSubErrorsWithPaths() {
        ContentVerdict.Rejected rejected = new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.MISSING_REQUIRED_FIELD, ContentPath.of("equipment"), "First error"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, ContentPath.of("equipment").key("05"), "Second error")));

        PlannerValidationException ex = PlannerValidationException.rejecting(rejected, CONTENT);

        assertThat(ex.getOriginalCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("MISSING_REQUIRED_FIELD", "equipment", "First error"),
                new ValidationError("INVALID_FIELD_TYPE", "equipment[05]", "Second error"));
        assertThat(ex.getMessage())
                .isEqualTo("[MISSING_REQUIRED_FIELD] First error; [INVALID_FIELD_TYPE] Second error");
        assertThat(ex.getFailedContent()).isEqualTo(CONTENT);
    }

    @Test
    void rejecting_WhenOneViolation_HasExactlyOneSubErrorUnderTheGenericCode() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                new Violation(ErrorCode.UNKNOWN_EGO, ContentPath.of("equipment"), "Only message"))), CONTENT);

        assertThat(ex.getOriginalCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_EGO", "equipment", "Only message"));
    }

    @Test
    void refusing_WhenAUserFacingViolation_KeepsItsCodeAndMessage() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.EMPTY_CONTENT, ContentPath.root(), "Content is empty or null")), CONTENT);

        assertThat(ex.getOriginalCode()).isEqualTo("EMPTY_CONTENT");
        assertThat(ex.getMessage()).isEqualTo("Content is empty or null");
        assertThat(ex.getBody().getProperties().get("code")).isEqualTo("EMPTY_CONTENT");
        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("EMPTY_CONTENT", "", "Content is empty or null"));
        assertThat(ex.getFailedContent()).isEqualTo(CONTENT);
    }

    @Test
    void refusing_WhenACodeIsNotUserFacing_MasksItOnTheWire() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.UNKNOWN_FIELD, ContentPath.root(), "Unknown fields: [theme]")), CONTENT);

        assertThat(ex.getOriginalCode()).isEqualTo("UNKNOWN_FIELD");
        assertThat(ex.getBody().getProperties().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getBody().getDetail()).isEqualTo("Invalid planner content structure");
    }

    @Test
    void refusing_WhenANoteIsOversized_CarriesTheNotePathAndKeepsTheBody() {
        PlannerValidationException ex = PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.SIZE_EXCEEDED, ContentPath.of("sectionNotes").key("floor1"),
                        "Note 'floor1' size 1100 exceeds limit 1024")), CONTENT);

        assertThat(ex.getSubErrors()).containsExactly(
                new ValidationError("SIZE_EXCEEDED", "sectionNotes[floor1]", "Note 'floor1' size 1100 exceeds limit 1024"));
        assertThat(ex.getBody().getStatus()).isEqualTo(400);
        assertThat(ex.getBody().getProperties())
                .isEqualTo(java.util.Map.of(
                        "code", "SIZE_EXCEEDED", "message", "Note 'floor1' size 1100 exceeds limit 1024"));
        assertThat(ex.getBody().getDetail()).isEqualTo("Note 'floor1' size 1100 exceeds limit 1024");
    }

    @Test
    void rejecting_WhenEveryViolationIsAnUnknownKeyword_AnswersUnknownKeywordOnTheWireListingThem() {
        PlannerValidationException ex = PlannerValidationException.rejecting(new ContentVerdict.Rejected(List.of(
                Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD, ContentPath.of("selectedKeywords").index(1),
                        "selectedKeywords", "Zilch"),
                Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD, ContentPath.of("selectedKeywords").index(2),
                        "selectedKeywords", "Nope"))),
                "{\"selectedKeywords\":[\"Sinking\",\"Zilch\",\"Nope\",\"AccelBullet\"]}");

        assertThat(ex.getOriginalCode()).isEqualTo("UNKNOWN_KEYWORD");
        assertThat(ex.getBody().getProperties().get("code")).isEqualTo("UNKNOWN_KEYWORD");
        assertThat(ex.getBody().getDetail()).isEqualTo("Unknown keywords: [Nope, Zilch]");
        assertThat(ex.reportable()).isFalse();
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

        assertThat(ex.getBody().getProperties().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getBody().getDetail()).isEqualTo("Invalid planner content structure");
    }
}
