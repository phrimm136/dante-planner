package org.danteplanner.backend.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.concurrent.atomic.AtomicLong;

public final class ThreadStatementCounter implements StatementInspector {

    private static final ThreadLocal<AtomicLong> COUNT = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        AtomicLong count = COUNT.get();
        if (count != null) {
            count.incrementAndGet();
        }
        return sql;
    }

    public static void start() {
        COUNT.set(new AtomicLong());
    }

    public static long stop() {
        long statements = COUNT.get().get();
        COUNT.remove();
        return statements;
    }
}
