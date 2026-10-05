package org.danteplanner.backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

public final class SseStream implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String CONNECTED_EVENT = "connected";

    private final HttpClient client;
    private final Stream<String> lines;
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private volatile RuntimeException ended;

    public record Frame(String event, String data) {
    }

    private SseStream(HttpClient client, HttpResponse<Stream<String>> response) {
        this.client = client;
        this.lines = response.body();
        Thread reader = new Thread(this::read, readerName(response.uri()));
        reader.setDaemon(true);
        reader.start();
    }

    public static SseStream user(int port, Cookie... cookies) {
        return open(port, "/api/sse/subscribe", cookies);
    }

    public static SseStream comments(int port, UUID plannerId, Cookie... cookies) {
        return open(port, "/api/planner/" + plannerId + "/comments/events", cookies);
    }

    private static SseStream open(int port, String path, Cookie... cookies) {
        return open(port, path, CONNECT_TIMEOUT, cookies);
    }

    static SseStream open(int port, String path, Duration readiness, Cookie... cookies) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "text/event-stream")
                .timeout(CONNECT_TIMEOUT);
        if (cookies.length > 0) {
            request.header("Cookie", Arrays.stream(cookies)
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<Stream<String>> response;
        try {
            response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            client.shutdownNow();
            throw new UncheckedIOException("could not open the event stream " + path, e);
        } catch (InterruptedException e) {
            client.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while opening the event stream " + path, e);
        }
        if (response.statusCode() != 200) {
            response.body().close();
            client.shutdownNow();
            throw new AssertionError("the event stream " + path + " answered " + response.statusCode());
        }
        SseStream stream = new SseStream(client, response);
        try {
            stream.next(CONNECTED_EVENT, readiness);
        } catch (RuntimeException | AssertionError notReady) {
            try {
                stream.close();
            } catch (RuntimeException closeFailure) {
                notReady.addSuppressed(closeFailure);
            }
            throw notReady;
        }
        return stream;
    }

    static String readerName(URI uri) {
        return "sse-stream-reader " + uri;
    }

    public String next(String event, Duration timeout) {
        return next(event, data -> true, timeout);
    }

    public String next(String event, Predicate<String> data, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            Frame frame;
            try {
                frame = remaining > 0 ? frames.poll(remaining, TimeUnit.NANOSECONDS) : null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for a " + event + " frame", e);
            }
            if (frame == null) {
                throw new AssertionError("no matching " + event + " frame arrived within " + timeout, ended);
            }
            if (frame.event().equals(event) && data.test(frame.data())) {
                return frame.data();
            }
        }
    }

    @Override
    public void close() {
        lines.close();
        client.shutdownNow();
    }

    private void read() {
        FrameParser parser = new FrameParser();
        try {
            lines.forEach(line -> parser.accept(line).ifPresent(frames::add));
        } catch (UncheckedIOException | IllegalStateException closed) {
            ended = closed;
        }
    }

    static final class FrameParser {

        private String event;
        private final List<String> data = new ArrayList<>();

        Optional<Frame> accept(String line) {
            if (line.isEmpty()) {
                return dispatch();
            }
            if (line.startsWith(":")) {
                return Optional.empty();
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            switch (field) {
                case "event" -> event = value;
                case "data" -> data.add(value);
                default -> {
                }
            }
            return Optional.empty();
        }

        private Optional<Frame> dispatch() {
            if (data.isEmpty()) {
                event = null;
                return Optional.empty();
            }
            Frame frame = new Frame(event == null ? "message" : event, String.join("\n", data));
            event = null;
            data.clear();
            return Optional.of(frame);
        }
    }
}
