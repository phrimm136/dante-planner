package org.danteplanner.backend.shared.redis;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;

final class HeldConnection {

    private final Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector;
    private CompletableFuture<StatefulRedisConnection<String, String>> attempt;
    private boolean closed;

    HeldConnection(Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector) {
        this.connector = connector;
    }

    synchronized CompletableFuture<String> ping() {
        if (closed) {
            return CompletableFuture.failedFuture(new RedisException("Redis role probe closed"));
        }
        if (attempt == null || !isUsable(attempt)) {
            discard();
            attempt = connector.get();
        }
        return attempt.thenCompose(connection -> connection.async().ping().toCompletableFuture());
    }

    synchronized void discardUnlessConnecting() {
        if (attempt != null && attempt.isDone()) {
            discard();
        }
    }

    synchronized void close() {
        closed = true;
        discardUnlessConnecting();
    }

    private void discard() {
        if (attempt != null) {
            var unused = attempt.thenCompose(StatefulRedisConnection::closeAsync);
            attempt = null;
        }
    }

    private static boolean isUsable(CompletableFuture<StatefulRedisConnection<String, String>> attempt) {
        return switch (attempt.state()) {
            case RUNNING -> true;
            case SUCCESS -> attempt.resultNow().isOpen();
            case FAILED, CANCELLED -> false;
        };
    }
}
