package org.danteplanner.backend.shared.redis;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;

final class HeldConnection {

    private final Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector;
    private CompletableFuture<StatefulRedisConnection<String, String>> attempt;
    private CompletableFuture<StatefulRedisConnection<String, String>> failedPingAttempt;
    private boolean closed;

    HeldConnection(Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector) {
        this.connector = connector;
    }

    synchronized CompletableFuture<String> ping() {
        if (closed) {
            return CompletableFuture.failedFuture(new RedisException("Redis role probe closed"));
        }
        if (attempt == null || attempt == failedPingAttempt || !isUsable(attempt)) {
            discard();
            attempt = connector.get();
        }
        CompletableFuture<StatefulRedisConnection<String, String>> pinged = attempt;
        return pinged.thenCompose(connection -> connection.async().ping().toCompletableFuture())
                .whenComplete((pong, failure) -> {
                    if (failure != null) {
                        pingFailedOn(pinged);
                    }
                });
    }

    // Lettuce fails a dropped connection's in-flight command before isOpen() turns false.
    private synchronized void pingFailedOn(CompletableFuture<StatefulRedisConnection<String, String>> pinged) {
        if (attempt == pinged) {
            failedPingAttempt = pinged;
        }
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
            failedPingAttempt = null;
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
