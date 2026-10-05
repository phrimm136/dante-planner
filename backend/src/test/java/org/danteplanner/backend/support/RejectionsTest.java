package org.danteplanner.backend.support;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class RejectionsTest {

    private static final String UNKNOWN_FIELD_CONTENT =
            TestDataFactory.VALID_CONTENT.replaceFirst("\\{", "{\"unknownField\":\"value\",");

    @Test
    void assertRejected_WhenNothingIsThrown_Fails() {
        assertThatThrownBy(() -> assertRejected(() -> { }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("expected a rejection");
    }

    @Test
    void assertRejected_WhenTheThrownExceptionIsNotADomainException_Fails() {
        assertThatThrownBy(() -> assertRejected(() -> {
            throw new IllegalStateException("not a rejection");
        }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("IllegalStateException");
    }

    @Test
    void withCode_WhenTheBodyCarriesTheGenericCode_PassesForIt() {
        assertThatCode(() -> assertRejected(() -> TestDataFactory.document(UNKNOWN_FIELD_CONTENT))
                .withCode("VALIDATION_ERROR"))
                .doesNotThrowAnyException();
    }

    @Test
    void withCode_WhenOnlyASubErrorCarriesTheCode_FailsNamingTheBodysCode() {
        assertThatThrownBy(() -> assertRejected(() -> TestDataFactory.document(UNKNOWN_FIELD_CONTENT))
                .withCode("UNKNOWN_FIELD"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("<UNKNOWN_FIELD>")
                .hasMessageContaining("<VALIDATION_ERROR>");
    }

    @Test
    void withCode_WhenTheOriginalCodeIsNotUserFacing_FailsNamingTheBodysCode() {
        assertThatThrownBy(() -> assertRejected(() -> {
            throw new PlannerValidationException("INVALID_CONTENT_VERSION", "bad version");
        }).withCode("INVALID_CONTENT_VERSION"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("<VALIDATION_ERROR>");
    }

    @Test
    void withCode_WhenAnotherDomainExceptionCarriesItInItsBody_Passes() {
        assertThatCode(() -> assertRejected(() -> {
            throw new PlannerNotFoundException(UUID.randomUUID());
        }).withCode("PLANNER_NOT_FOUND"))
                .doesNotThrowAnyException();
    }

    @Test
    void withViolation_WhenASubErrorCarriesTheCodeAtThePath_Passes() {
        assertThatCode(() -> assertRejected(() -> TestDataFactory.document(UNKNOWN_FIELD_CONTENT))
                .withViolation("UNKNOWN_FIELD", "unknownField"))
                .doesNotThrowAnyException();
    }

    @Test
    void withViolation_WhenTheOriginalCodeCarriesIt_Passes() {
        assertThatCode(() -> assertRejected(() -> {
            throw new PlannerValidationException("INVALID_CONTENT_VERSION", "bad version");
        }).withViolation("INVALID_CONTENT_VERSION"))
                .doesNotThrowAnyException();
    }

    @Test
    void withViolation_WhenNeitherTheOriginalCodeNorASubErrorCarriesIt_FailsNamingWhatWasCarried() {
        assertThatThrownBy(() -> assertRejected(() -> TestDataFactory.document(UNKNOWN_FIELD_CONTENT))
                .withViolation("SIZE_EXCEEDED"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SIZE_EXCEEDED")
                .hasMessageContaining("UNKNOWN_FIELD");
    }

    @Test
    void withViolation_WhenTheMatchingSubErrorSitsElsewhere_FailsNamingThePath() {
        assertThatThrownBy(() -> assertRejected(() -> TestDataFactory.document(UNKNOWN_FIELD_CONTENT))
                .withViolation("UNKNOWN_FIELD", "equipment"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("<equipment>")
                .hasMessageContaining("unknownField");
    }

    @Test
    void withViolation_WhenTheRejectionIsNotAValidationException_Fails() {
        assertThatThrownBy(() -> assertRejected(() -> {
            throw new PlannerNotFoundException(UUID.randomUUID());
        }).withViolation("PLANNER_NOT_FOUND"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PlannerNotFoundException carries no violations");
    }
}
