package org.danteplanner.backend.shared.exception;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The database constraints whose violation carries a known meaning, and the outcome each produces.
 *
 * <p>Rows are keyed on the qualified constraint identifier MySQL reports for error 1062
 * ({@code Duplicate entry '…' for key '<table>.<key>'}), which Hibernate exposes typed through
 * {@code org.hibernate.exception.ConstraintViolationException#getConstraintName()}. Keying on table
 * and index names rather than on entity or repository types is what lets the shared layer own this
 * table without importing a feature package.</p>
 *
 * <p>A violation of any constraint absent from this table falls to
 * {@link ConstraintViolationOutcome#UNEXPECTED_CONFLICT}.</p>
 */
public enum KnownConstraint {

    /**
     * The planner core row carries the client-supplied UUID, so a collision is a retryable race
     * rather than a defect. Scoped to the primary key: a future unique index on the same table
     * would express a different rule.
     */
    PLANNER_ID("planner", "PRIMARY", ConstraintViolationOutcome.UUID_COLLISION),

    /**
     * Every unique key on a user-action junction table states "this user already did this to this
     * target", so any of them means the action was already performed.
     */
    PLANNER_VOTE("planner_votes", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    PLANNER_BOOKMARK("planner_bookmarks", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    PLANNER_REPORT("planner_reports", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION),
    COMMENT_REPORT("planner_comment_reports", Keys.ANY_UNIQUE, ConstraintViolationOutcome.DUPLICATE_ACTION);

    /**
     * Key-name wildcards. Held in a nested type because an enum constant's arguments cannot
     * reference a static field of the enum itself.
     */
    public static final class Keys {

        /** The row covers a violation of any unique key on its table. */
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
     * Finds the row covering a qualified constraint identifier.
     *
     * <p>Comparison folds case under {@link Locale#ROOT}: the default locale must not decide the
     * outcome, and Turkish lower-cases the {@code I} in {@code PRIMARY} to a dotless {@code ı}.</p>
     *
     * @param qualifiedConstraintName {@code <table>.<key>} as the driver reports it, may be null
     * @return the covering row, or empty when the identifier is unqualified, null or unlisted
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

    public ConstraintViolationOutcome outcome() {
        return outcome;
    }
}
