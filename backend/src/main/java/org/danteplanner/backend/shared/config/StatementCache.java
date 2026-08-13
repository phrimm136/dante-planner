package org.danteplanner.backend.shared.config;

import java.util.Map;

/**
 * The MySQL prepared-statement cache settings every MySQL-bound pool carries — the hand-built
 * routing pools read them from here, and {@code spring.datasource.hikari.data-source-properties.*}
 * restates them for the pool Spring Boot builds itself.
 *
 * <p>{@code rewriteBatchedStatements} is absent by decision: it folds a versioned batch into one
 * multi-row statement whose affected-row count no longer identifies the individual update, which is
 * exactly what {@code @Version} optimistic locking reads to detect a lost write.</p>
 */
public final class StatementCache {

    public static final int PREP_STMT_CACHE_SIZE = 256;

    public static final int PREP_STMT_CACHE_SQL_LIMIT = 2048;

    /** Driver property names in Connector/J's own spelling, with their values. */
    public static final Map<String, String> DRIVER_PROPERTIES = Map.of(
            "cachePrepStmts", "true",
            "useServerPrepStmts", "true",
            "prepStmtCacheSize", String.valueOf(PREP_STMT_CACHE_SIZE),
            "prepStmtCacheSqlLimit", String.valueOf(PREP_STMT_CACHE_SQL_LIMIT));

    /** Never applied to any pool — see the class note. */
    public static final String BANNED_DRIVER_PROPERTY = "rewriteBatchedStatements";

    private StatementCache() {
        // Utility class - prevent instantiation
    }
}
