package org.danteplanner.backend.support;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Integrity violations shaped the way the running stack produces them.
 *
 * <p>A hand-written {@link DataIntegrityViolationException} carries a message and nothing else,
 * while the code under test reads the violated key off the wrappers Hibernate and Spring add. A
 * unit test asserting against the bare exception would therefore pass against a service that
 * classifies nothing.</p>
 */
public final class IntegrityViolations {

    private IntegrityViolations() {
    }

    /**
     * A duplicate-key insert as the whole stack reports it: MySQL error 1062 under SQLState 23000,
     * the key as {@link MySQLDialect} extracts it, and the Hibernate and Spring wrappers.
     *
     * @param key the violated unique key, table-qualified as MySQL 8.0.19 and later report it
     * @return the exception the repository call raises
     */
    public static DataIntegrityViolationException duplicateEntry(String key) {
        SQLException driverFailure = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'abcde' for key '" + key + "'", "23000", 1062);

        ConstraintViolationException hibernateFailure = new ConstraintViolationException(
                driverFailure.getMessage(), driverFailure,
                new MySQLDialect().getViolatedConstraintNameExtractor()
                        .extractConstraintName(driverFailure));

        return new DataIntegrityViolationException(hibernateFailure.getMessage(), hibernateFailure);
    }
}
