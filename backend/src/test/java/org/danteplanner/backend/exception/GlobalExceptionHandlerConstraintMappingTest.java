package org.danteplanner.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.exception.KnownConstraint;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * The wire contract for a database constraint violation: which status and which error code each
 * violated constraint produces, and which of them raise a Sentry alert. Clients key retry behaviour
 * on the code, so the expectations here are spelled out literally rather than read back from the
 * production table.
 *
 * <p>The fixtures reproduce the exception MySQL 8 and Hibernate actually deliver — error 1062 under
 * SQLState 23000, wrapped in a Hibernate {@code ConstraintViolationException} whose
 * {@code getConstraintName()} carries {@code <table>.<key>}.</p>
 *
 * <p>Constraint lookup must also not depend on the JVM's default locale: Turkish lower-cases
 * {@code I} to a dotless {@code ı}, so a key name carrying a capital I ({@code PRIMARY}) can stop
 * matching its row and turn an expected duplicate-key race into an unexpected conflict plus an
 * alert.</p>
 */
class GlobalExceptionHandlerConstraintMappingTest {

    private static final String SQL = "insert into planner_votes (user_id,planner_id) values (?,?)";

    private record ContractRow(String constraintName, HttpStatus status, String code, String message) {
    }

    private static Stream<ContractRow> frozenContract() {
        return Stream.of(
                new ContractRow("planner.PRIMARY", HttpStatus.CONFLICT, "UUID_COLLISION",
                        "Plan ID already exists. Please retry with a new ID."),
                new ContractRow("planner_votes.PRIMARY", HttpStatus.CONFLICT, "DUPLICATE_ACTION",
                        "Action already performed"),
                new ContractRow("planner_bookmarks.PRIMARY", HttpStatus.CONFLICT, "DUPLICATE_ACTION",
                        "Action already performed"),
                new ContractRow("planner_reports.uk_report_user_planner", HttpStatus.CONFLICT, "DUPLICATE_ACTION",
                        "Action already performed"),
                new ContractRow("planner_comment_reports.uk_comment_report_reporter_comment", HttpStatus.CONFLICT,
                        "DUPLICATE_ACTION", "Action already performed"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frozenContract")
    void a_listed_constraint_keeps_its_frozen_response(ContractRow row) {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handle(duplicateKey(row.constraintName()));

        assertEquals(row.status(), response.getStatusCode());
        assertEquals(row.code(), response.getBody().code());
        assertEquals(row.message(), response.getBody().message());
    }

    /**
     * Derives the covered set from the production table so a new {@link KnownConstraint} constant
     * cannot ship without a literal contract row stating what clients will see.
     */
    @Test
    void every_listed_constraint_carries_a_contract_row() {
        Set<KnownConstraint> covered = frozenContract()
                .map(ContractRow::constraintName)
                .map(KnownConstraint::matching)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(KnownConstraint.class)));

        assertEquals(EnumSet.allOf(KnownConstraint.class), covered);
    }

    @Test
    void an_unlisted_unique_key_is_reported_as_a_conflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handle(duplicateKey("planner_stats.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Resource conflict", response.getBody().message());
    }

    @Test
    void a_foreign_key_violation_is_reported_as_invalid_data() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handle(otherIntegrityFailure(
                "Cannot add or update a child row: a foreign key constraint fails", 1452, "fk_vote_planner"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().code());
        assertEquals("Invalid data", response.getBody().message());
    }

    /**
     * The constraint name is a decoy: it is a listed one, so only the uniqueness gate keeps a
     * NOT NULL failure out of the duplicate-action branch. Without the gate this returns 409.
     */
    @Test
    void a_not_null_violation_on_a_listed_table_stays_invalid_data() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handle(otherIntegrityFailure(
                "Column 'planner_id' cannot be null", 1048, "planner_votes.PRIMARY"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().code());
    }

    /**
     * Uniqueness is still recognised when nothing in the chain is a Hibernate exception, from the
     * vendor error code alone. The constraint name is then unavailable, so the outcome is the
     * unlisted-conflict row rather than the 400 an unrecognised failure would produce.
     */
    @Test
    void a_vendor_duplicate_code_alone_is_recognised_as_unique() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1-2' for key 'planner_views.PRIMARY'", "23000", 1062));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handle(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
    }

    @Test
    void a_duplicate_key_subclass_is_recognised_as_unique() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handle(new DuplicateKeyException("a row with that key already exists"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
    }

    @Test
    void an_expected_race_raises_no_alert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(duplicateKey("planner_votes.PRIMARY"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
        }
    }

    @Test
    void an_unlisted_conflict_raises_an_alert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(duplicateKey("planner_stats.PRIMARY"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
        }
    }

    @Test
    @DisplayName("a UNIQUE violation maps to 409 under a Turkish default locale")
    void handleDataIntegrityViolation_WhenTurkishLocale_MapsUniqueToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handleUnder(Locale.forLanguageTag("tr"), duplicateKey("planner_votes.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", response.getBody().code());
    }

    @Test
    @DisplayName("a UNIQUE violation maps identically under the root locale")
    void handleDataIntegrityViolation_WhenRootLocale_MapsUniqueToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handleUnder(Locale.ROOT, duplicateKey("planner_votes.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", response.getBody().code());
    }

    /**
     * {@code planner.PRIMARY} is the row whose key name takes part in the comparison, so it is the
     * one a locale-sensitive case fold can break: Turkish folds the I in PRIMARY to a dotless ı.
     */
    @Test
    void a_capital_i_in_a_key_name_survives_a_turkish_locale() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handleUnder(Locale.forLanguageTag("tr"), duplicateKey("planner.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("UUID_COLLISION", response.getBody().code());
    }

    private static ResponseEntity<GlobalExceptionHandler.ErrorResponse> handle(DataIntegrityViolationException ex) {
        return new GlobalExceptionHandler(mock(CookieUtils.class), new ObjectMapper())
                .handleDataIntegrityViolation(ex);
    }

    private static ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleUnder(
            Locale locale, DataIntegrityViolationException ex) {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return handle(ex);
        } finally {
            Locale.setDefault(original);
        }
    }

    /** Reproduces MySQL error 1062 as Spring delivers it after Hibernate's dialect conversion. */
    private static DataIntegrityViolationException duplicateKey(String constraintName) {
        SQLException driverFailure = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '1-2' for key '" + constraintName + "'", "23000", 1062);
        return translated(new ConstraintViolationException("could not execute statement", driverFailure, SQL,
                ConstraintViolationException.ConstraintKind.UNIQUE, constraintName), constraintName);
    }

    /** Reproduces a non-uniqueness integrity failure: foreign key, NOT NULL or check. */
    private static DataIntegrityViolationException otherIntegrityFailure(
            String driverMessage, int errorCode, String constraintName) {
        SQLException driverFailure = new SQLIntegrityConstraintViolationException(driverMessage, "23000", errorCode);
        return translated(new ConstraintViolationException("could not execute statement", driverFailure, SQL,
                ConstraintViolationException.ConstraintKind.OTHER, constraintName), constraintName);
    }

    private static DataIntegrityViolationException translated(
            ConstraintViolationException hibernateFailure, String constraintName) {
        return new DataIntegrityViolationException(
                hibernateFailure.getMessage() + "; SQL [" + SQL + "]; constraint [" + constraintName + "]",
                hibernateFailure);
    }
}
