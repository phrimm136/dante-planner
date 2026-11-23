package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.config.ReadSourceSelector;

@Slf4j
public final class ReplicaGtidWait {

    private static final String WAIT_GTID_SQL = "SELECT WAIT_FOR_EXECUTED_GTID_SET(?, ?)";
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final Integer APPLIED = 0;

    private ReplicaGtidWait() {
    }

    public static boolean isApplied(ReadSourceSelector.Source source, String gtidSet, Duration bound) {
        try {
            return Objects.equals(new JdbcTemplate(source.dataSource())
                    .queryForObject(WAIT_GTID_SQL, Integer.class, gtidSet, bound.toNanos() / NANOS_PER_SECOND), APPLIED);
        } catch (DataAccessException e) {
            log.warn("GTID wait failed on {} for gtid={}, counting it as not applied", source.name(), gtidSet, e);
            return false;
        }
    }
}
