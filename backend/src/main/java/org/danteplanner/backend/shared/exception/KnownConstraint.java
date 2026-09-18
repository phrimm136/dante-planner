package org.danteplanner.backend.shared.exception;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Rows are keyed on the qualified constraint identifier MySQL reports for error 1062
 * ({@code Duplicate entry '…' for key '<table>.<key>'}), which Hibernate exposes typed through
 * {@code org.hibernate.exception.ConstraintViolationException#getConstraintName()}.
 */
public enum KnownConstraint {

    PLANNER_ID("planner", "PRIMARY", ConstraintViolationOutcome.UUID_COLLISION),

    PLANNER_VOTE("planner_votes", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    PLANNER_BOOKMARK("planner_bookmarks", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    PLANNER_REPORT("planner_reports", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    COMMENT_REPORT("planner_comment_reports", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION);

    /**
     * An enum constant's arguments cannot reference a static field of the enum itself.
     */
    public static final class Keys {

        public static final String ANY_UNIQUE = "*";

        private Keys() {
        }
    }

    private static final char QUALIFIER_SEPARATOR = '.';

    private final String table;
    private final String keyName;
    private final ConstraintViolationOutcome outcome;

    KnownConstraint(String table, String keyName, ConstraintViolationOutcome outcome) {
        this.table = table;
        this.keyName = keyName;
        this.outcome = outcome;
    }

    /**
     * Comparison folds case under {@link Locale#ROOT}: Turkish lower-cases the {@code I} in
     * {@code PRIMARY} to a dotless {@code ı}.
     */
    public static Optional<KnownConstraint> matching(String qualifiedConstraintName) {
        if (qualifiedConstraintName == null) {
            return Optional.empty();
        }
        String normalized = qualifiedConstraintName.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(QUALIFIER_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String violatedTable = normalized.substring(0, separator);
        String violatedKey = normalized.substring(separator + 1);
        return Arrays.stream(values())
                .filter(constraint -> constraint.covers(violatedTable, violatedKey))
                .findFirst();
    }

    private boolean covers(String violatedTable, String violatedKey) {
        return table.toLowerCase(Locale.ROOT).equals(violatedTable)
                && (Keys.ANY_UNIQUE.equals(keyName) || keyName.toLowerCase(Locale.ROOT).equals(violatedKey));
    }

    public String table() {
        return table;
    }

    public ConstraintViolationOutcome outcome() {
        return outcome;
    }
}
