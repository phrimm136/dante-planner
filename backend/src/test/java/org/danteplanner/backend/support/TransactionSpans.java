package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

public final class TransactionSpans {

    private static final ThreadLocal<Span> CURRENT = new ThreadLocal<>();

    private TransactionSpans() {
    }

    public interface Installed extends AutoCloseable {

        @Override
        void close();
    }

    public static Installed install(PlatformTransactionManager transactionManager) {
        ConfigurableTransactionManager manager = (ConfigurableTransactionManager) transactionManager;
        Collection<TransactionExecutionListener> listeners = manager.getTransactionExecutionListeners();
        List<TransactionExecutionListener> withSpans = new ArrayList<>(listeners);
        withSpans.add(new SpanListener());
        manager.setTransactionExecutionListeners(withSpans);
        return () -> manager.setTransactionExecutionListeners(listeners);
    }

    public static void within(Span span, Runnable body) {
        CURRENT.set(span);
        try {
            body.run();
        } finally {
            CURRENT.remove();
        }
    }

    public static final class Span {

        private long begunAt = -1;
        private long endedAt = -1;
        private int depth;

        void begun() {
            if (depth++ == 0 && begunAt < 0) {
                begunAt = System.nanoTime();
            }
        }

        void ended() {
            if (--depth == 0 && endedAt < 0) {
                endedAt = System.nanoTime();
            }
        }

        public boolean overlaps(Span other) {
            return endedAt >= 0 && other.endedAt >= 0
                    && Math.max(begunAt, other.begunAt) < Math.min(endedAt, other.endedAt);
        }
    }

    private static final class SpanListener implements TransactionExecutionListener {

        @Override
        public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
            Span span = CURRENT.get();
            if (span != null && beginFailure == null) {
                span.begun();
            }
        }

        @Override
        public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
            Span span = CURRENT.get();
            if (span != null) {
                span.ended();
            }
        }

        @Override
        public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
            Span span = CURRENT.get();
            if (span != null) {
                span.ended();
            }
        }
    }
}
