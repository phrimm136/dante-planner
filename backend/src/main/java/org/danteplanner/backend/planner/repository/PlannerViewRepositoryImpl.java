package org.danteplanner.backend.planner.repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.springframework.jdbc.core.JdbcTemplate;

public class PlannerViewRepositoryImpl implements PlannerViewRepositoryCustom {

    private static final String INSERT_IGNORE_PREFIX =
            "INSERT IGNORE INTO planner_views (planner_id, viewer_hash, view_date, created_at) VALUES ";
    private static final String ROW_PLACEHOLDER = "(?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    // Build over the @Primary (routing) datasource rather than an autoconfigured JdbcTemplate,
    // which backs off when multiple datasources are present. The batch insert is non-read-only,
    // so the routing datasource sends it to the primary. Mirrors GtidWriteCapture.
    public PlannerViewRepositoryImpl(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Map<UUID, Integer> insertIgnoreReturningNewCounts(
            Collection<PlannerViewId> views, Instant createdAt) {
        Timestamp createdTs = Timestamp.from(createdAt);
        Map<UUID, List<PlannerViewId>> byPlanner = views.stream()
                .collect(Collectors.groupingBy(PlannerViewId::getPlannerId, LinkedHashMap::new,
                        Collectors.toList()));

        Map<UUID, Integer> newCounts = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PlannerViewId>> entry : byPlanner.entrySet()) {
            List<PlannerViewId> group = entry.getValue();
            String sql = INSERT_IGNORE_PREFIX + group.stream()
                    .map(v -> ROW_PLACEHOLDER)
                    .collect(Collectors.joining(", "));
            Object[] args = new Object[group.size() * 4];
            int i = 0;
            for (PlannerViewId view : group) {
                args[i++] = toBinary(view.getPlannerId());
                args[i++] = view.getViewerHash();
                args[i++] = view.getViewDate();
                args[i++] = createdTs;
            }
            int inserted = jdbcTemplate.update(sql, args);
            if (inserted > 0) {
                newCounts.put(entry.getKey(), inserted);
            }
        }
        return newCounts;
    }

    /** Big-endian encoding matching how Hibernate persists UUID to a BINARY(16) column. */
    private static byte[] toBinary(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
