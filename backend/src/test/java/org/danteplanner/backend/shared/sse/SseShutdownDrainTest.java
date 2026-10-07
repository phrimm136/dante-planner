package org.danteplanner.backend.shared.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import org.junit.jupiter.api.Test;

import org.assertj.core.api.SoftAssertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseShutdownDrainTest {

    private static final String PROFILE = "sse-shutdown-drain-probe";
    private static final long SHUTDOWN_PHASE_TIMEOUT_MS = 5_000;
    /** Well under the phase timeout, well over a context close with nothing in flight. */
    private static final long PROMPT_CLOSE_MS = 2_000;
    private static final int OPEN_STREAMS = 3;

    @Test
    void contextClose_WhenStreamsAreOpen_CompletesEveryEmitterPromptly() throws Exception {
        int streams = OPEN_STREAMS;
        ServletWebServerApplicationContext context = start();
        ProbeSseService service = context.getBean(ProbeSseService.class);
        List<InputStream> bodies = openStreams(context.getWebServer().getPort(), streams);
        assertThat(service.openCount()).isEqualTo(streams);

        long started = System.nanoTime();
        context.close();
        long closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(closeMs).as("context close time with %d open streams", streams)
                    .isLessThan(PROMPT_CLOSE_MS);
            soft.assertThat(service.completed.getCount()).as("emitters left uncompleted after close").isZero();
            soft.assertThat(service.openCount()).as("registry entries left after close").isZero();
        });
        for (InputStream body : bodies) {
            body.close();
        }
    }

    @Test
    void contextClose_WhenNoStreamIsOpen_ClosesPromptly() {
        ServletWebServerApplicationContext context = start();

        long started = System.nanoTime();
        context.close();
        long closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(closeMs).as("context close time with no open streams").isLessThan(PROMPT_CLOSE_MS);
    }

    @Test
    void onApplicationEvent_WhenEmittersAlreadyCompletedOrErrored_CompletesTheOpenOnesWithoutThrowing() {
        ProbeSseService service = new ProbeSseService();
        SseEmitter completed = new SseEmitter();
        completed.complete();
        SseEmitter errored = new SseEmitter();
        errored.completeWithError(new IOException("peer gone"));
        SseEmitter open = new SseEmitter();
        service.connect(completed);
        service.connect(errored);
        service.connect(open);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            assertThatCode(() -> service.onApplicationEvent(new ContextClosedEvent(context)))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> open.send(SseEmitter.event().comment("heartbeat")))
                .as("a send after the drain finds the open emitter completed")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void subscribe_WhenTheDrainHasRun_EndsTheStreamAtOnceWithoutRegistering() throws Exception {
        ServletWebServerApplicationContext context = start();
        ProbeSseService service = context.getBean(ProbeSseService.class);
        service.onApplicationEvent(new ContextClosedEvent(context));

        HttpRequest request = HttpRequest.newBuilder(streamUri(context.getWebServer().getPort())).GET().build();
        CompletableFuture<HttpResponse<String>> stream =
                HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());

        assertThat(stream).as("a subscription after the drain ends instead of staying open")
                .succeedsWithin(Duration.ofMillis(PROMPT_CLOSE_MS));
        assertThat(service.openCount()).as("registry entries after a post-drain subscription").isZero();

        long started = System.nanoTime();
        context.close();
        long closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(closeMs).as("context close time after a post-drain subscription").isLessThan(PROMPT_CLOSE_MS);
    }

    @Test
    void register_WhenTheDrainRunsMidRegistration_CompletesTheNewEmitter() throws Exception {
        ProbeSseService service = new ProbeSseService();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            service.duringRegister = () -> service.onApplicationEvent(new ContextClosedEvent(context));

            SseEmitter emitter = service.subscribe();

            assertThatThrownBy(() -> emitter.send(SseEmitter.event().comment("heartbeat")))
                    .as("an emitter the drain's pass missed is completed by its own registration")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    private static URI streamUri(int port) {
        return URI.create("http://localhost:" + port + "/probe/stream");
    }

    private static ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(ProbeApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(PROFILE)
                .properties(
                        "server.port=0",
                        "spring.lifecycle.timeout-per-shutdown-phase=" + SHUTDOWN_PHASE_TIMEOUT_MS + "ms",
                        "spring.main.banner-mode=off")
                .run();
    }

    private static List<InputStream> openStreams(int port, int count) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        List<InputStream> bodies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HttpRequest request = HttpRequest.newBuilder(streamUri(port))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            InputStream body = client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            String line;
            do {
                line = reader.readLine();
            } while (line != null && !line.startsWith("event:connected"));
            assertThat(line).as("connected event on stream %d", i).isNotNull();
            bodies.add(body);
        }
        return bodies;
    }

    @ImportAutoConfiguration({
            LifecycleAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    static class ProbeApplication {

        @Bean
        ProbeSseService probeSseService() {
            return new ProbeSseService();
        }

        @Bean
        ProbeStreamController probeStreamController(ProbeSseService service) {
            return new ProbeStreamController(service);
        }
    }

    @Profile(PROFILE)
    @RestController
    static class ProbeStreamController {

        private final ProbeSseService service;

        ProbeStreamController(ProbeSseService service) {
            this.service = service;
        }

        @GetMapping("/probe/stream")
        SseEmitter stream() throws Exception {
            return service.subscribe();
        }
    }

    static class ProbeSseService extends AbstractSseService<String> {

        final CountDownLatch completed = new CountDownLatch(OPEN_STREAMS);
        Runnable duringRegister = () -> { };

        ProbeSseService() {
            super(new SyncTaskExecutor());
        }

        SseEmitter subscribe() throws Exception {
            SseEmitter emitter = register("probe", UUID.randomUUID());
            emitter.onCompletion(completed::countDown);
            return emitter;
        }

        void connect(SseEmitter emitter) {
            emitters.computeIfAbsent("probe", k -> new CopyOnWriteArrayList<>())
                    .add(new EmitterEntry(UUID.randomUUID(), null, emitter));
        }

        int openCount() {
            return connectionCount("probe");
        }

        @Override
        protected void beforeRegister(String key, CopyOnWriteArrayList<EmitterEntry> connections) {
            duringRegister.run();
        }

        @Override
        protected void onUnsubscribed(String key, UUID deviceId) {
        }

        @Override
        protected void onHeartbeatFailure(String key, UUID deviceId) {
        }
    }
}
