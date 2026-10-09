package org.danteplanner.backend.integration.consistency;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.consistency.Changed;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Rejected;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID KEY = UUID.randomUUID();

    @Test
    void recorder_WhenResponsesCoverEachOutcome_WritesLinesHistoryReadParses(@TempDir Path directory)
            throws IOException {
        Path file = directory.resolve("history.jsonl");
        AtomicLong clock = new AtomicLong();
        String found = MAPPER.writeValueAsString(PlannerResponse.builder().id(KEY).syncVersion(3).build());
        String key = KEY.toString();

        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(file), clock::incrementAndGet)) {
            recorder.read("s1", key, () -> new Response(200, found));
            recorder.delete("s1", key, () -> new Response(204, ""));
            recorder.read("s2", key, () -> new Response(404, ""));
            recorder.read("s3", key, () -> new Response(503, ""));
            recorder.save("s1", key, () -> new Response(409, ""));
            recorder.save("s1", key, () -> new Response(500, ""));
            recorder.save("s1", key, () -> {
                throw new SocketException("connection reset");
            });
        }

        List<String> outcomes = Files.readAllLines(file).stream().map(HistoryRecorderTest::outcome).toList();
        assertThat(outcomes).containsExactly("ok", "ok", "ok", "unknown", "fail", "unknown", "unknown");
        assertThat(History.read(file).operations())
                .extracting(HistoryRecorderTest::result)
                .containsExactly(new Found(3), new Deleted(), new NotFound(), new ReadFailed(0), new Rejected(409),
                        new Unknown(), new Unknown());
    }

    @Test
    void recorder_WhenPublishAndPublicReadResponsesCoverEachOutcome_WritesLinesHistoryReadParses(
            @TempDir Path directory) throws IOException {
        Path file = directory.resolve("history.jsonl");
        AtomicLong clock = new AtomicLong();
        String publicView = MAPPER.writeValueAsString(
                PublishedPlannerDetailResponse.builder().id(KEY).syncVersion(4).build());
        String key = KEY.toString();

        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(file), clock::incrementAndGet)) {
            recorder.publish("s1", key, () -> new Response(200, ""));
            recorder.publish("s1", key, () -> new Response(400, ""));
            recorder.publish("s1", key, () -> new Response(503, ""));
            recorder.publish("s1", key, () -> {
                throw new SocketException("connection reset");
            });
            recorder.unpublish("s2", key, () -> new Response(200, ""));
            recorder.publicRead("anon", key, () -> new Response(200, publicView));
            recorder.publicRead("anon", key, () -> new Response(404, ""));
        }

        List<String> outcomes = Files.readAllLines(file).stream().map(HistoryRecorderTest::outcome).toList();
        assertThat(outcomes).containsExactly("ok", "fail", "unknown", "unknown", "ok", "ok", "ok");
        assertThat(History.read(file).operations())
                .extracting(HistoryRecorderTest::result)
                .containsExactly(new Changed(), new Rejected(400), new Unknown(), new Unknown(), new Changed(),
                        new Found(4), new NotFound());
    }

    private static Object result(Operation operation) {
        return switch (operation) {
            case Save save -> save.result();
            case Delete delete -> delete.result();
            case Read read -> read.result();
            case Publish publish -> publish.result();
            case Unpublish unpublish -> unpublish.result();
            case PublicRead read -> read.result();
            default -> operation;
        };
    }

    private static String outcome(String line) {
        try {
            return MAPPER.readTree(line).path("outcome").textValue();
        } catch (IOException e) {
            throw new IllegalStateException(line, e);
        }
    }
}
