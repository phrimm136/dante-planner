package org.danteplanner.backend.exception;

import io.sentry.Sentry;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.Problems;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

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
class ApiExceptionHandlerConstraintMappingTest {

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
    void listedConstraint_WhenMapped_KeepsFrozenResponse(ContractRow row) {
        ResponseEntity<Object> response = handle(duplicateKey(row.constraintName()));

        assertEquals(row.status(), response.getStatusCode());
        assertEquals(row.code(), codeOf(response));
        assertEquals(row.message(), detailOf(response));
    }

    /**
     * Derives the covered set from the production table so a new {@link KnownConstraint} constant
     * cannot ship without a literal contract row stating what clients will see.
     */
    @Test
    void listedConstraint_WhenTableChecked_CarriesContractRow() {
        Set<KnownConstraint> covered = frozenContract()
                .map(ContractRow::constraintName)
                .map(KnownConstraint::matching)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(KnownConstraint.class)));

        assertEquals(EnumSet.allOf(KnownConstraint.class), covered);
    }

    @Test
    void unlistedUniqueKey_WhenMapped_IsReportedAsConflict() {
        ResponseEntity<Object> response = handle(duplicateKey("planner_stats.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", codeOf(response));
        assertEquals("Resource conflict", detailOf(response));
    }

    @Test
    void foreignKeyViolation_WhenMapped_IsReportedAsInvalidData() {
        ResponseEntity<Object> response = handle(otherIntegrityFailure(
                "Cannot add or update a child row: a foreign key constraint fails", 1452, "fk_vote_planner"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", codeOf(response));
        assertEquals("Invalid data", detailOf(response));
    }

    /**
     * The constraint name is a decoy: it is a listed one, so only the uniqueness gate keeps a
     * NOT NULL failure out of the duplicate-action branch. Without the gate this returns 409.
     */
    @Test
    void notNullViolation_WhenOnListedTable_StaysInvalidData() {
        ResponseEntity<Object> response = handle(otherIntegrityFailure(
                "Column 'planner_id' cannot be null", 1048, "planner_votes.PRIMARY"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", codeOf(response));
    }

    /**
     * Uniqueness is still recognised when nothing in the chain is a Hibernate exception, from the
     * vendor error code alone. The constraint name is then unavailable, so the outcome is the
     * unlisted-conflict row rather than the 400 an unrecognised failure would produce.
     */
    @Test
    void vendorDuplicateCode_WhenAlone_IsRecognisedAsUnique() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1-2' for key 'planner_views.PRIMARY'", "23000", 1062));

        ResponseEntity<Object> response = handle(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", codeOf(response));
    }

    @Test
    void duplicateKeySubclass_WhenMapped_IsRecognisedAsUnique() {
        ResponseEntity<Object> response =
                handle(new DuplicateKeyException("a row with that key already exists"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", codeOf(response));
    }

    @Test
    void expectedRace_WhenMapped_RaisesNoAlert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(duplicateKey("planner_votes.PRIMARY"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
        }
    }

    @Test
    void unlistedConflict_WhenMapped_RaisesAnAlert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(duplicateKey("planner_stats.PRIMARY"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
        }
    }

    @Test
    @DisplayName("a UNIQUE violation maps to 409 under a Turkish default locale")
    void handleDataIntegrityViolation_WhenTurkishLocale_MapsUniqueToConflict() {
        ResponseEntity<Object> response =
                handleUnder(Locale.forLanguageTag("tr"), duplicateKey("planner_votes.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", codeOf(response));
    }

    @Test
    @DisplayName("a UNIQUE violation maps identically under the root locale")
    void handleDataIntegrityViolation_WhenRootLocale_MapsUniqueToConflict() {
        ResponseEntity<Object> response =
                handleUnder(Locale.ROOT, duplicateKey("planner_votes.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_ACTION", codeOf(response));
    }

    /**
     * {@code planner.PRIMARY} is the row whose key name takes part in the comparison, so it is the
     * one a locale-sensitive case fold can break: Turkish folds the I in PRIMARY to a dotless ı.
     */
    @Test
    void capitalIInKeyName_WhenTurkishLocale_Survives() {
        ResponseEntity<Object> response =
                handleUnder(Locale.forLanguageTag("tr"), duplicateKey("planner.PRIMARY"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("UUID_COLLISION", codeOf(response));
    }

    private static ResponseEntity<Object> handle(DataIntegrityViolationException ex) {
        return new ApiExceptionHandler(mock(CookieUtils.class))
                .handleDataIntegrityViolation(ex, new ServletWebRequest(new MockHttpServletRequest()));
    }

    private static ResponseEntity<Object> handleUnder(
            Locale locale, DataIntegrityViolationException ex) {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return handle(ex);
        } finally {
            Locale.setDefault(original);
        }
    }

    private static String codeOf(ResponseEntity<Object> response) {
        return (String) body(response).getProperties().get(Problems.CODE);
    }

    private static String detailOf(ResponseEntity<Object> response) {
        return body(response).getDetail();
    }

    private static ProblemDetail body(ResponseEntity<Object> response) {
        return (ProblemDetail) response.getBody();
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
