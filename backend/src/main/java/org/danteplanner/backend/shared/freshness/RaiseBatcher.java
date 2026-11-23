package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RaiseBatcher implements AutoCloseable {

    static final String THREAD_NAME = "marker-raise";
    static final int MAX_BATCH = 256;

    private static final String RAISE_SCRIPT = """
            for _, key in ipairs(KEYS) do
              for i = 2, #ARGV, 2 do
                local current = tonumber(redis.call('HGET', key, ARGV[i]))
                if current == nil or current < tonumber(ARGV[i + 1]) then
                  redis.call('HSET', key, ARGV[i], ARGV[i + 1])
                end
              end
              redis.call('PEXPIRE', key, ARGV[1])
            end
            return #KEYS
            """;

    record Endpoint(RedisClient client, RedisURI uri) {
    }

    private static final long NOT_ACKNOWLEDGED = 0;

    private record RaiseRequest(String[] keys, String[] arguments, CompletableFuture<Boolean> raised) {
    }

    private final Supplier<Endpoint> endpointFactory;
    private final int waitReplicas;
    private final Duration waitBound;
    private final Duration margin;
    private final Duration bound;
    private final Counter raiseFailures;
    private final Counter waitTimeouts;
    private final Counter raiseAbandoned;
    private final LongSupplier clock;
    private final BlockingQueue<RaiseRequest> queue = new LinkedBlockingQueue<>();
    private WorkerState workerState = new WorkerState.Idle();
    private volatile Opened opened = new Opened.None();
    private Link link = new Link.Unlinked();

    RaiseBatcher(Supplier<Endpoint> endpointFactory, int waitReplicas, Duration waitBound, Duration margin,
            Counter raiseFailures, Counter waitTimeouts, Counter raiseAbandoned, LongSupplier clock) {
        this.endpointFactory = endpointFactory;
        this.waitReplicas = waitReplicas;
        this.waitBound = waitBound;
        this.margin = margin;
        this.bound = waitBound.plus(margin);
        this.raiseFailures = raiseFailures;
        this.waitTimeouts = waitTimeouts;
        this.raiseAbandoned = raiseAbandoned;
        this.clock = clock;
    }

    boolean raise(String[] keys, String[] arguments) {
        RaiseRequest request = new RaiseRequest(keys, arguments, new CompletableFuture<>());
        if (!enqueue(request)) {
            log.warn("Freshness markers {} not raised: marker store closed", List.of(keys));
            fail(List.of(request));
            return false;
        }
        try {
            return request.raised().get(bound.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            raiseAbandoned.increment();
            return false;
        } catch (ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        WorkerState before;
        synchronized (this) {
            before = workerState;
            workerState = new WorkerState.Closed();
        }
        switch (before) {
            case WorkerState.Closed closed -> {
                return;
            }
            case WorkerState.Started started -> {
                started.thread().interrupt();
                join(started.thread());
            }
            case WorkerState.Idle idle -> { }
        }
        List<RaiseRequest> pending = new ArrayList<>();
        queue.drainTo(pending);
        if (!pending.isEmpty()) {
            log.warn("Freshness markers of {} requests not raised: marker store closed", pending.size());
        }
        fail(pending);
        switch (opened) {
            case Opened.Endpointed endpointed -> endpointed.endpoint().client().shutdown();
            case Opened.None none -> { }
        }
    }

    private synchronized boolean enqueue(RaiseRequest request) {
        switch (workerState) {
            case WorkerState.Closed closed -> {
                return false;
            }
            case WorkerState.Started started when started.thread().isAlive() -> {
            }
            case WorkerState.Started started -> startWorker();
            case WorkerState.Idle idle -> startWorker();
        }
        queue.add(request);
        return true;
    }

    private void startWorker() {
        Thread worker = new Thread(this::drain, THREAD_NAME);
        worker.setDaemon(true);
        worker.setUncaughtExceptionHandler((thread, error) ->
                log.error("Freshness marker worker {} died; the next raise starts a new one", thread.getName(), error));
        worker.start();
        workerState = new WorkerState.Started(worker);
    }

    private void join(Thread running) {
        try {
            running.join(bound.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drain() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<RaiseRequest> batch = new ArrayList<>();
                batch.add(queue.take());
                queue.drainTo(batch, MAX_BATCH - 1);
                boolean sent;
                try {
                    sent = send(batch);
                } catch (VirtualMachineError e) {
                    fail(batch);
                    throw e;
                }
                if (sent) {
                    batch.forEach(request -> request.raised().complete(true));
                } else {
                    fail(batch);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeConnection();
        }
    }

    private void fail(List<RaiseRequest> requests) {
        requests.forEach(request -> {
            raiseFailures.increment();
            request.raised().complete(false);
        });
    }

    private boolean send(List<RaiseRequest> batch) {
        long deadline = clock.getAsLong() + bound.toNanos();
        try {
            long acknowledged = replicate(batch, deadline);
            if (acknowledged < waitReplicas) {
                waitTimeouts.increment();
            }
            return true;
        } catch (RedisException e) {
            log.warn("Freshness markers of {} requests not raised: marker store unavailable", batch.size(), e);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            log.error("Freshness markers of {} requests not raised: the raise failed", batch.size(), e);
        }
        closeConnection();
        return false;
    }

    private long replicate(List<RaiseRequest> batch, long deadline) {
        Link.Linked linked = linked(deadline);
        try {
            return pipeline(linked.connection().async(), linked.scriptSha(), batch, deadline);
        } catch (RedisNoScriptException e) {
            Link.Linked reloaded = new Link.Linked(linked.connection(),
                    await(linked.connection().async().scriptLoad(RAISE_SCRIPT), deadline));
            link = reloaded;
            return pipeline(reloaded.connection().async(), reloaded.scriptSha(), batch, deadline);
        }
    }

    private long pipeline(RedisAsyncCommands<String, String> commands, String scriptSha, List<RaiseRequest> batch,
            long deadline) {
        long waitNanos = Math.min(waitBound.toNanos(), deadline - clock.getAsLong() - margin.toNanos());
        List<RedisFuture<Long>> raises = batch.stream()
                .map(request -> commands.<Long>evalsha(
                        scriptSha, ScriptOutputType.INTEGER, request.keys(), request.arguments()))
                .toList();
        RedisFuture<Long> replicated = waitNanos > 0
                ? commands.waitForReplication(waitReplicas, Math.max(1, TimeUnit.NANOSECONDS.toMillis(waitNanos)))
                : null;
        raises.forEach(raise -> await(raise, deadline));
        if (replicated == null || deadline - clock.getAsLong() <= 0) {
            return NOT_ACKNOWLEDGED;
        }
        return await(replicated, deadline);
    }

    private Link.Linked linked(long deadline) {
        return switch (LinkUse.of(link, isLinkOpen())) {
            case LinkUse.Keep keep -> keep.linked();
            case LinkUse.Replace replace -> relink(deadline);
        };
    }

    private boolean isLinkOpen() {
        return switch (link) {
            case Link.Linked linked -> linked.connection().isOpen();
            case Link.Unlinked unlinked -> false;
        };
    }

    private Link.Linked relink(long deadline) {
        closeConnection();
        Endpoint endpoint = switch (opened) {
            case Opened.Endpointed endpointed -> endpointed.endpoint();
            case Opened.None none -> {
                Endpoint created = endpointFactory.get();
                opened = new Opened.Endpointed(created);
                yield created;
            }
        };
        StatefulRedisConnection<String, String> connection = endpoint.client().connect(StringCodec.UTF8, endpoint.uri());
        try {
            Link.Linked linked =
                    new Link.Linked(connection, await(connection.async().scriptLoad(RAISE_SCRIPT), deadline));
            link = linked;
            return linked;
        } catch (RuntimeException | Error e) {
            connection.closeAsync();
            throw e;
        }
    }

    private void closeConnection() {
        switch (link) {
            case Link.Linked linked -> {
                linked.connection().closeAsync();
                link = new Link.Unlinked();
            }
            case Link.Unlinked unlinked -> { }
        }
    }

    private sealed interface WorkerState {

        record Idle() implements WorkerState {
        }

        record Started(Thread thread) implements WorkerState {
        }

        record Closed() implements WorkerState {
        }
    }

    private sealed interface Opened {

        record None() implements Opened {
        }

        record Endpointed(Endpoint endpoint) implements Opened {
        }
    }

    private sealed interface LinkUse {

        record Keep(Link.Linked linked) implements LinkUse {
        }

        record Replace() implements LinkUse {
        }

        static LinkUse of(Link link, boolean open) {
            return switch (link) {
                case Link.Linked linked when open -> new Keep(linked);
                case Link.Linked linked -> new Replace();
                case Link.Unlinked unlinked -> new Replace();
            };
        }
    }

    private sealed interface Link {

        record Unlinked() implements Link {
        }

        record Linked(StatefulRedisConnection<String, String> connection, String scriptSha) implements Link {
        }
    }

    private <T> T await(RedisFuture<T> future, long deadline) {
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0) {
            future.cancel(true);
            throw new RedisCommandTimeoutException("Raise deadline passed before the reply");
        }
        return LettuceFutures.awaitOrCancel(future, remaining, TimeUnit.NANOSECONDS);
    }
}
