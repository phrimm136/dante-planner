package org.danteplanner.backend.shared.redis;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.List;

final class HeldConnection {

    private static final CompletableFuture<Void> NOTHING_TO_CLOSE = CompletableFuture.completedFuture(null);

    private final Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector;
    private State state = new State.Disconnected();

    HeldConnection(Supplier<CompletableFuture<StatefulRedisConnection<String, String>>> connector) {
        this.connector = connector;
    }

    synchronized CompletableFuture<String> ping() {
        return switch (state) {
            case State.Closed closed -> CompletableFuture.failedFuture(new RedisException("Redis role probe closed"));
            case State.Held held when !held.pingFailed() && isUsable(held.attempt()) -> pingOn(held.attempt());
            case State.Held held -> pingOn(reconnect());
            case State.Disconnected disconnected -> pingOn(reconnect());
        };
    }

    private CompletableFuture<String> pingOn(CompletableFuture<StatefulRedisConnection<String, String>> pinged) {
        return pinged.thenCompose(connection -> connection.async().ping().toCompletableFuture())
                .whenComplete((pong, failure) -> {
                    if (failure != null) {
                        pingFailedOn(pinged);
                    }
                });
    }

    private CompletableFuture<StatefulRedisConnection<String, String>> reconnect() {
        var unused = discard();
        CompletableFuture<StatefulRedisConnection<String, String>> attempt = connector.get();
        state = new State.Held(attempt, false);
        return attempt;
    }

    // Lettuce fails a dropped connection's in-flight command before isOpen() turns false.
    @SuppressWarnings("ReferenceEquality")
    private synchronized void pingFailedOn(CompletableFuture<StatefulRedisConnection<String, String>> pinged) {
        switch (state) {
            case State.Held held when held.attempt() == pinged -> state = new State.Held(pinged, true);
            case State.Held held -> { }
            case State.Disconnected disconnected -> { }
            case State.Closed closed -> { }
        }
    }

    synchronized CompletableFuture<Void> discardUnlessConnecting() {
        return switch (state) {
            case State.Held held when held.attempt().isDone() -> discard();
            case State.Held held -> NOTHING_TO_CLOSE;
            case State.Disconnected disconnected -> NOTHING_TO_CLOSE;
            case State.Closed closed -> {
                state = closed.withoutSettled();
                yield NOTHING_TO_CLOSE;
            }
        };
    }

    synchronized CompletableFuture<Void> close() {
        CompletableFuture<Void> closing = discardUnlessConnecting();
        state = switch (state) {
            case State.Held held -> new State.Closed(List.of(held.attempt()));
            case State.Disconnected disconnected -> new State.Closed(List.of());
            case State.Closed closed -> closed;
        };
        return closing;
    }

    private CompletableFuture<Void> discard() {
        return switch (state) {
            case State.Held held -> {
                state = new State.Disconnected();
                yield held.attempt().thenCompose(StatefulRedisConnection::closeAsync);
            }
            case State.Disconnected disconnected -> NOTHING_TO_CLOSE;
            case State.Closed closed -> NOTHING_TO_CLOSE;
        };
    }

    private static boolean isUsable(CompletableFuture<StatefulRedisConnection<String, String>> attempt) {
        return switch (attempt.state()) {
            case RUNNING -> true;
            case SUCCESS -> attempt.resultNow().isOpen();
            case FAILED, CANCELLED -> false;
        };
    }

    private sealed interface State {

        record Disconnected() implements State {
        }

        record Held(CompletableFuture<StatefulRedisConnection<String, String>> attempt, boolean pingFailed)
                implements State {
        }

        record Closed(List<CompletableFuture<StatefulRedisConnection<String, String>>> inFlight) implements State {

            Closed withoutSettled() {
                inFlight.stream().filter(CompletableFuture::isDone).forEach(attempt -> {
                    var unused = attempt.thenCompose(StatefulRedisConnection::closeAsync);
                });
                return new Closed(inFlight.stream().filter(attempt -> !attempt.isDone()).toList());
            }
        }
    }
}
