package org.danteplanner.backend.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseStreamTest {

    @Test
    void frameParser_WhenFedSpringsWireFormat_YieldsEachNamedFrameAndSkipsComments() {
        String wire = wire(SseEmitter.event().name("connected").data("{}"))
                + wire(SseEmitter.event().comment("heartbeat"))
                + wire(SseEmitter.event().name("comment:added").data("{\"plannerId\":\"p-1\"}"));

        assertThat(parse(wire)).containsExactly(
                new SseStream.Frame("connected", "{}"),
                new SseStream.Frame("comment:added", "{\"plannerId\":\"p-1\"}"));
    }

    @Test
    void frameParser_WhenDataSpansSeveralLines_JoinsThemWithNewlines() {
        String wire = wire(SseEmitter.event().name("account_suspended").data("first\nsecond"));

        assertThat(parse(wire)).containsExactly(new SseStream.Frame("account_suspended", "first\nsecond"));
    }

    @Test
    void open_WhenTheConnectedFrameNeverArrives_ClosesTheHalfBuiltStream() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService handlers = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(handlers);
        server.createContext("/silent", exchange -> holdOpen(exchange, release));
        server.start();
        String path = "/silent";
        String reader = SseStream.readerName(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path));
        try {
            assertThatThrownBy(() -> SseStream.open(server.getAddress().getPort(), path, Duration.ofMillis(200)))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("no matching connected frame arrived");

            assertThat(awaitGone(reader, Duration.ofSeconds(5)))
                    .as("the reader thread of a stream that never became ready")
                    .isTrue();
        } finally {
            release.countDown();
            server.stop(0);
            handlers.shutdownNow();
        }
    }

    private static void holdOpen(HttpExchange exchange, CountDownLatch release) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().flush();
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exchange.close();
    }

    private static boolean awaitGone(String threadName, Duration timeout) throws InterruptedException {
        Optional<Thread> reader = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(threadName))
                .findFirst();
        if (reader.isEmpty()) {
            return true;
        }
        reader.get().join(timeout.toMillis());
        return !reader.get().isAlive();
    }

    private static String wire(SseEventBuilder event) {
        return event.build().stream()
                .map(part -> part.getData().toString())
                .collect(Collectors.joining());
    }

    private static List<SseStream.Frame> parse(String wire) {
        SseStream.FrameParser parser = new SseStream.FrameParser();
        return wire.lines()
                .map(parser::accept)
                .flatMap(Optional::stream)
                .toList();
    }
}
