package org.danteplanner.backend.shared.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RoleConnector;

public final class SseSubscription implements RoleConnector, AutoCloseable {

    private static final CompletableFuture<Void> LISTENING = CompletableFuture.completedFuture(null);

    private final BooleanSupplier listening;
    private final Runnable resubscribe;
    private final Executor attempts;
    private final ReentrantLock attemptLock = new ReentrantLock();
    private CompletableFuture<Void> attempt;
    private boolean closed;

    public SseSubscription(BooleanSupplier listening, Runnable resubscribe, Executor attempts) {
        this.listening = listening;
        this.resubscribe = resubscribe;
        this.attempts = attempts;
    }

    @Override
    public RedisRole role() {
        return RedisRole.SSE_LOCAL;
    }

    @Override
    public CompletableFuture<Void> connect() {
        attemptLock.lock();
        try {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("SSE subscription closed"));
            }
            if (listening.getAsBoolean()) {
                return LISTENING;
            }
            if (attempt == null || attempt.isDone()) {
                attempt = CompletableFuture.runAsync(resubscribe, attempts);
            }
            return attempt;
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        } finally {
            attemptLock.unlock();
        }
    }

    @Override
    public void close() {
        attemptLock.lock();
        try {
            closed = true;
        } finally {
            attemptLock.unlock();
        }
    }
}
