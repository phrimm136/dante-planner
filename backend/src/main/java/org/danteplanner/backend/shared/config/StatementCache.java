package org.danteplanner.backend.shared.config;

import java.util.Map;

/**
 * {@code rewriteBatchedStatements} folds a versioned batch into one multi-row statement whose
 * affected-row count no longer identifies the individual update, which is what {@code @Version}
 * optimistic locking reads to detect a lost write.
 */
public final class StatementCache {

    public static final int PREP_STMT_CACHE_SIZE = 256;

    public static final int PREP_STMT_CACHE_SQL_LIMIT = 2048;

    public static final Map<String, String> DRIVER_PROPERTIES = Map.of(
            "cachePrepStmts", "true",
            "useServerPrepStmts", "true",
            "prepStmtCacheSize", String.valueOf(PREP_STMT_CACHE_SIZE),
            "prepStmtCacheSqlLimit", String.valueOf(PREP_STMT_CACHE_SQL_LIMIT));

    public static final String BANNED_DRIVER_PROPERTY = "rewriteBatchedStatements";

    private StatementCache() {
    }
}
