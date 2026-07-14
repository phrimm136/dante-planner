package org.danteplanner.backend.shared.gtid;

import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * Captures the GTID committed by the current request's write so it can be echoed
 * back to the client in the read-your-writes cookie.
 *
 * <p>Reads {@code @@gtid_executed} on the primary immediately after the write committed (the
 * mechanics §5 verified fallback for the {@code session_track_gtids=OWN_GTID} OK-packet path).
 * The query is non-read-only, so through the {@code @Primary} routing datasource it reaches the
 * primary pool; the value is a conservative superset that already includes the write's GTID.</p>
 */
public class GtidWriteCapture {

    private static final Logger log = LoggerFactory.getLogger(GtidWriteCapture.class);

    private static final String CAPTURE_GTID_SQL = "SELECT @@gtid_executed";

    private final JdbcTemplate jdbcTemplate;

    public GtidWriteCapture(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
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
