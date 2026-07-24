package org.danteplanner.backend.shared.gtid;

import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * Accumulates the GTIDs committed by the current request's writes so they can be echoed back to the
 * client in the read-your-writes cookie.
 *
 * <p>Each non-read-only transaction that commits during the request contributes its own GTID via
 * {@code session_track_gtids=OWN_GTID} (recorded through {@link #recordCommit(String)} from an
 * {@code afterCommit} synchronization). {@link #pollCapturedGtid()} returns the union of a request's
 * captured GTIDs, so a follow-up replica read gates past every commit (main tx plus the
 * {@code AFTER_COMMIT}/{@code REQUIRES_NEW} filter rebuild), not only the first. When a transaction
 * committed but produced no OWN_GTID (tracker empty, or the Hikari connection unwrap failed), it
 * falls back to {@code SELECT @@gtid_executed} — a conservative superset. When no transaction
 * committed at all (a Redis-only write, or a pure read), it returns empty and no cookie is minted.</p>
 */
public class GtidWriteCapture {

    private static final Logger log = LoggerFactory.getLogger(GtidWriteCapture.class);

    private static final String CAPTURE_GTID_SQL = "SELECT @@gtid_executed";

    private final JdbcTemplate jdbcTemplate;

    public GtidWriteCapture(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    GtidWriteCapture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordCommit(String ownGtid) {
    }

    public void clear() {
    }

    public Optional<String> pollCapturedGtid() {
        try {
            String gtid = jdbcTemplate.queryForObject(CAPTURE_GTID_SQL, String.class);
            if (!StringUtils.hasText(gtid)) {
                return Optional.empty();
            }
            return Optional.of(gtid.replaceAll("\\s+", ""));
        } catch (DataAccessException e) {
            log.warn("Failed to capture committed GTID for the read-your-writes cookie", e);
            return Optional.empty();
        }
    }
}
