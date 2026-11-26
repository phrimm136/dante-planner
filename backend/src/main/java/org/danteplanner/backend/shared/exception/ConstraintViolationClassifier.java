package org.danteplanner.backend.shared.exception;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import org.hibernate.exception.ConstraintViolationException;

public final class ConstraintViolationClassifier {

    /** MySQL's duplicate-entry error, reported under the integrity-constraint SQLState. */
    private static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;
    private static final String INTEGRITY_CONSTRAINT_VIOLATION_SQLSTATE = "23000";

    /** The SQL:2011 SQLState for a unique violation, used by drivers that do not overload 23000. */
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private static final int MAX_CAUSE_DEPTH = 16;

    private ConstraintViolationClassifier() {
    }

    public static ConstraintViolationKind classify(DataIntegrityViolationException ex) {
        Cause<ConstraintViolationException> hibernateViolation = causeOfType(ex, ConstraintViolationException.class);
        if (!isUniquenessViolation(ex, hibernateViolation)) {
            return ConstraintViolationKind.INVALID_DATA;
        }
        return switch (hibernateViolation) {
            case Cause.Found<ConstraintViolationException> found -> switch (ConstraintName.of(found.cause())) {
                case ConstraintName.Named named -> KnownConstraint.matching(named.qualified())
                        .fold(KnownConstraint::outcome, () -> ConstraintViolationKind.UNEXPECTED_CONFLICT);
                case ConstraintName.Unnamed unnamed -> ConstraintViolationKind.UNEXPECTED_CONFLICT;
            };
            case Cause.Absent<ConstraintViolationException> absent -> ConstraintViolationKind.UNEXPECTED_CONFLICT;
        };
    }

    public sealed interface ConstraintName {

        record Named(String qualified) implements ConstraintName {
        }

        record Unnamed() implements ConstraintName {
        }

        static ConstraintName of(ConstraintViolationException violation) {
            String name = violation.getConstraintName();
            return name == null ? new Unnamed() : new Named(name);
        }
    }

    private sealed interface Cause<T> {

        record Found<T>(T cause) implements Cause<T> {
        }

        record Absent<T>() implements Cause<T> {
        }
    }

    private static boolean isUniquenessViolation(
            DataIntegrityViolationException ex, Cause<ConstraintViolationException> hibernateViolation) {
        if (ex instanceof DuplicateKeyException) {
            return true;
        }
        boolean uniqueKind = switch (hibernateViolation) {
            case Cause.Found<ConstraintViolationException> found ->
                    found.cause().getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
            case Cause.Absent<ConstraintViolationException> absent -> false;
        };
        return uniqueKind || switch (causeOfType(ex, SQLException.class)) {
            case Cause.Found<SQLException> found -> indicatesDuplicateKey(found.cause());
            case Cause.Absent<SQLException> absent -> false;
        };
    }

    private static boolean indicatesDuplicateKey(SQLException ex) {
        String sqlState = ex.getSQLState();
        return UNIQUE_VIOLATION_SQLSTATE.equals(sqlState)
                || (INTEGRITY_CONSTRAINT_VIOLATION_SQLSTATE.equals(sqlState)
                        && ex.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE);
    }

    private static <T extends Throwable> Cause<T> causeOfType(Throwable ex, Class<T> type) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return new Cause.Found<>(type.cast(current));
            }
            current = current.getCause();
        }
        return new Cause.Absent<>();
    }
}
