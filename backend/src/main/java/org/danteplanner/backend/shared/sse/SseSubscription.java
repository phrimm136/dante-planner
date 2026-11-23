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
    private State state = new State.Idle();

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
            return switch (state) {
                case State.Closed closed -> CompletableFuture.failedFuture(
                        new IllegalStateException("SSE subscription closed"));
                case State.Attempting attempting when listening.getAsBoolean() -> LISTENING;
                case State.Attempting attempting when !attempting.attempt().isDone() -> attempting.attempt();
                case State.Attempting attempting -> startAttempt();
                case State.Idle idle -> listening.getAsBoolean() ? LISTENING : startAttempt();
            };
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
            state = new State.Closed();
        } finally {
            attemptLock.unlock();
        }
    }

    private CompletableFuture<Void> startAttempt() {
        CompletableFuture<Void> attempt = CompletableFuture.runAsync(resubscribe, attempts);
        state = new State.Attempting(attempt);
        return attempt;
    }

    private sealed interface State {

        record Idle() implements State {
        }

        record Attempting(CompletableFuture<Void> attempt) implements State {
        }

        record Closed() implements State {
        }
    }
}
