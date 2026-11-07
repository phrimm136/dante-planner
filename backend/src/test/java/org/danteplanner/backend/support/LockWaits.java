package org.danteplanner.backend.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.springframework.jdbc.core.JdbcTemplate;

public final class LockWaits {

    private static final String WAITING_ISOLATION_LEVELS = """
            SELECT t.trx_isolation_level FROM information_schema.innodb_trx t
            JOIN information_schema.processlist p ON p.id = t.trx_mysql_thread_id
            WHERE t.trx_state = 'LOCK WAIT' AND p.db = DATABASE()
            """;

    private LockWaits() {
    }

    public static List<String> await(JdbcTemplate jdbc, int waiters, BooleanSupplier settled, String what) {
        AtomicReference<List<String>> waiting = new AtomicReference<>(List.of());
        Hang.until(() -> {
            waiting.set(jdbc.queryForList(WAITING_ISOLATION_LEVELS, String.class));
            return settled.getAsBoolean() || waiting.get().size() >= waiters;
        }, what);
        return waiting.get();
    }
}
