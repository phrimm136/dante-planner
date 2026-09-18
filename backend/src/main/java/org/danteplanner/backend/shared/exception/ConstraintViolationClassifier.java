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

    public static ConstraintViolationOutcome classify(DataIntegrityViolationException ex) {
        ConstraintViolationException hibernateViolation = causeOfType(ex, ConstraintViolationException.class);
        if (!isUniquenessViolation(ex, hibernateViolation)) {
            return ConstraintViolationOutcome.INVALID_DATA;
        }
        String constraintName = hibernateViolation == null ? null : hibernateViolation.getConstraintName();
        return KnownConstraint.matching(constraintName)
                .map(KnownConstraint::outcome)
                .orElse(ConstraintViolationOutcome.UNEXPECTED_CONFLICT);
    }

    private static boolean isUniquenessViolation(
            DataIntegrityViolationException ex, ConstraintViolationException hibernateViolation) {
        if (ex instanceof DuplicateKeyException) {
            return true;
        }
        if (hibernateViolation != null
                && hibernateViolation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
            return true;
        }
        SQLException sqlException = causeOfType(ex, SQLException.class);
        return sqlException != null && indicatesDuplicateKey(sqlException);
    }

    private static boolean indicatesDuplicateKey(SQLException ex) {
        String sqlState = ex.getSQLState();
        return UNIQUE_VIOLATION_SQLSTATE.equals(sqlState)
                || (INTEGRITY_CONSTRAINT_VIOLATION_SQLSTATE.equals(sqlState)
                        && ex.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE);
    }

    private static <T extends Throwable> T causeOfType(Throwable ex, Class<T> type) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
