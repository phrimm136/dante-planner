package org.danteplanner.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Constraint-violation classification must not depend on the JVM's default locale.
 *
 * <p>Turkish lower-cases {@code I} to a dotless {@code i}, so the driver keywords carrying a capital
 * I ("UNIQUE", "PRIMARY KEY") stop matching and an expected duplicate-key race is reclassified as an
 * unexpected 400 plus a Sentry alert.</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerConstraintMappingTest {

    @Mock CookieUtils cookieUtils;

    private ResponseEntity<GlobalExceptionHandler.ErrorResponse> classify(String driverMessage, Locale locale) {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return new GlobalExceptionHandler(cookieUtils, new ObjectMapper())
                    .handleDataIntegrityViolation(new DataIntegrityViolationException(driverMessage));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("a UNIQUE violation maps to 409 under a Turkish default locale")
    void handleDataIntegrityViolation_WhenTurkishLocale_MapsUniqueToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = classify(
                "could not execute statement; UNIQUE constraint violated on planner_votes",
                Locale.forLanguageTag("tr"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", response.getBody().code());
    }

    @Test
    @DisplayName("a UNIQUE violation maps identically under the root locale")
    void handleDataIntegrityViolation_WhenRootLocale_MapsUniqueToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = classify(
                "could not execute statement; UNIQUE constraint violated on planner_votes",
                Locale.ROOT);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", response.getBody().code());
    }
}
