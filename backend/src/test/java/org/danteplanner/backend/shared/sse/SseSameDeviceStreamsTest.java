package org.danteplanner.backend.shared.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

import org.danteplanner.backend.support.Hang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Two tabs of one browser share the device cookie, so they subscribe under one device id. A stream
 * the server still holds open must either stay in the registry, where the heartbeat sweep reaches
 * it, or be completed.
 */
class SseSameDeviceStreamsTest {

    private static final String KEY = "user";

    @Test
    void register_WhenTheSameDeviceSubscribesAgain_KeepsBothStreamsRegistered() throws IOException {
        ProbeSseService service = new ProbeSseService();
        UUID deviceId = UUID.randomUUID();

        SseEmitter firstTab = service.register(KEY, deviceId, null);
        SseEmitter secondTab = service.register(KEY, deviceId, null);

        assertThat(service.registeredEmitters()).containsExactly(firstTab, secondTab);
    }

    @Test
    void remove_WhenOneOfTwoSameDeviceStreamsEnds_KeepsItsSiblingRegistered() throws IOException {
        ProbeSseService service = new ProbeSseService();
        UUID deviceId = UUID.randomUUID();

        SseEmitter firstTab = service.register(KEY, deviceId, null);
        SseEmitter secondTab = service.register(KEY, deviceId, null);
        // What the first emitter's onCompletion, onTimeout and onError callbacks run.
        service.remove(KEY, service.entryOf(firstTab));

        assertThat(service.registeredEmitters()).containsExactly(secondTab);
    }

    @Test
    void remove_WhenTheLastStreamEndsTwice_DropsTheKeyAndDoesNotThrow() throws IOException {
        ProbeSseService service = new ProbeSseService();
        SseEmitter onlyTab = service.register(KEY, UUID.randomUUID(), null);
        var entry = service.entryOf(onlyTab);

        service.remove(KEY, entry);

        assertThatCode(() -> service.remove(KEY, entry)).doesNotThrowAnyException();
        assertThat(service.holdsKey()).isFalse();
    }

    @Test
    void complete_WhenOneOfTwoSameDeviceStreamsCompletes_KeepsItsSiblingRegistered() throws Exception {
        UUID deviceId = UUID.randomUUID();
        try (ServletWebServerApplicationContext context = SseShutdownDrainTest.start()) {
            var service = context.getBean(SseShutdownDrainTest.ProbeSseService.class);
            int port = context.getWebServer().getPort();
            BufferedReader firstTab = openStream(port, deviceId);
            Hang.until(() -> service.openCount() == 1, "the first stream's registration");
            BufferedReader secondTab = openStream(port, deviceId);
            Hang.until(() -> service.openCount() == 2, "the second stream's registration");
            List<SseEmitter> arrived = service.openEmitters();

            arrived.get(0).complete();
            Hang.get(CompletableFuture.runAsync(() -> readToEnd(firstTab)), "the first stream's response ending");
            Hang.until(() -> service.openCount() < 2, "the registry dropping the completed stream");

            assertThat(service.openEmitters()).containsExactly(arrived.get(1));
            secondTab.close();
        }
    }

    private static BufferedReader openStream(int port, UUID deviceId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(SseShutdownDrainTest.streamUri(port, deviceId)).GET().build();
        var body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String line;
        do {
            line = reader.readLine();
        } while (line != null && !line.startsWith("event:connected"));
        assertThat(line).as("connected event").isNotNull();
        return reader;
    }

    private static void readToEnd(BufferedReader reader) {
        try {
            while (reader.readLine() != null) {
                continue;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class ProbeSseService extends AbstractSseService<String> {

        private ProbeSseService() {
            super(new SyncTaskExecutor());
        }

        private List<SseEmitter> registeredEmitters() {
            var connections = emitters.get(KEY);
            return connections == null ? List.of() : connections.stream().map(EmitterEntry::emitter).toList();
        }

        private EmitterEntry entryOf(SseEmitter emitter) {
            return emitters.get(KEY).stream()
                    .filter(entry -> entry.emitter().equals(emitter))
                    .findFirst()
                    .orElseThrow();
        }

        private boolean holdsKey() {
            return emitters.containsKey(KEY);
        }

        @Override
        protected void onUnsubscribed(String key, UUID deviceId) {
        }

        @Override
        protected void onHeartbeatFailure(String key, UUID deviceId) {
        }
    }
}
