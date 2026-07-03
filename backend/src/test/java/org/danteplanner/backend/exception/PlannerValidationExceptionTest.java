package org.danteplanner.backend.exception;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlannerValidationExceptionTest {

    @Test
    @DisplayName("Single-error constructor should produce empty subErrors list")
    void singleErrorConstructor_WhenUsed_HasEmptySubErrors() {
        PlannerValidationException ex = new PlannerValidationException("MY_CODE", "My message");

        assertEquals("MY_CODE", ex.getErrorCode());
        assertEquals("My message", ex.getMessage());
        assertTrue(ex.getSubErrors().isEmpty());
    }

    @Test
    @DisplayName("combined() should aggregate all errors into subErrors with VALIDATION_ERROR code")
    void combined_WhenMultipleErrors_AggregatesIntoSubErrors() {
        List<PlannerValidationException> errors = List.of(
                new PlannerValidationException("CODE_A", "First error"),
                new PlannerValidationException("CODE_B", "Second error")
        );

        PlannerValidationException combined = PlannerValidationException.combined(errors);

        assertEquals("VALIDATION_ERROR", combined.getErrorCode());
        assertEquals(2, combined.getSubErrors().size());
        assertEquals("CODE_A", combined.getSubErrors().get(0).code());
        assertEquals("First error", combined.getSubErrors().get(0).message());
        assertEquals("CODE_B", combined.getSubErrors().get(1).code());
        assertEquals("Second error", combined.getSubErrors().get(1).message());
        assertTrue(combined.getMessage().contains("[CODE_A] First error"));
        assertTrue(combined.getMessage().contains("[CODE_B] Second error"));
    }

    @Test
    @DisplayName("combined() with one error should produce exactly one sub-error")
    void combined_SingleError_HasExactlyOneSubError() {
        PlannerValidationException combined = PlannerValidationException.combined(
                List.of(new PlannerValidationException("ONLY_CODE", "Only message"))
        );

        assertEquals("VALIDATION_ERROR", combined.getErrorCode());
        assertEquals(1, combined.getSubErrors().size());
        assertEquals("ONLY_CODE", combined.getSubErrors().get(0).code());
        assertEquals("Only message", combined.getSubErrors().get(0).message());
    }
}
