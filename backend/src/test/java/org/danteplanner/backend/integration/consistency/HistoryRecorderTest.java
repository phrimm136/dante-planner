package org.danteplanner.backend.integration.consistency;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.support.TestJsonMappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.consistency.Changed;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.HistoryFormatException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper PROBLEM_MAPPER = TestJsonMappers.springDefaults();
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

    @Test
    void recorder_When503CarriesTheCodeOfItsKind_RecordsFailWithTheCodeAndHistoryReadParses(
            @TempDir Path directory) throws IOException {
        Path file = directory.resolve("history.jsonl");
        AtomicLong clock = new AtomicLong();
        String writeUnavailable = problem(DegradationErrorConstants.DB_UNAVAILABLE.code());
        String readUnavailable = problem(DegradationErrorConstants.READ_UNAVAILABLE.code());
        String deadlock = problem("DEADLOCK");
        String key = KEY.toString();

        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(file), clock::incrementAndGet)) {
            recorder.save("s1", key, () -> new Response(503, writeUnavailable));
            recorder.save("s1", key, () -> new Response(503, deadlock));
            recorder.read("s2", key, () -> new Response(503, readUnavailable));
            recorder.delete("s1", key, () -> new Response(503, writeUnavailable));
            recorder.publish("s1", key, () -> new Response(503, writeUnavailable));
            recorder.unpublish("s1", key, () -> new Response(503, writeUnavailable));
            recorder.publicRead("anon", key, () -> new Response(503, readUnavailable));
            recorder.list("s3", () -> new Response(503, readUnavailable));
            recorder.save("s1", key, () -> new Response(500, writeUnavailable));
        }

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).extracting(HistoryRecorderTest::outcome)
                .containsExactly("fail", "unknown", "fail", "fail", "fail", "fail", "fail", "fail", "unknown");
        assertThat(lines).extracting(line -> field(line, "code"))
                .containsExactly("WRITE_TEMPORARILY_UNAVAILABLE", null, "READ_TEMPORARILY_UNAVAILABLE",
                        "WRITE_TEMPORARILY_UNAVAILABLE", "WRITE_TEMPORARILY_UNAVAILABLE",
                        "WRITE_TEMPORARILY_UNAVAILABLE", "READ_TEMPORARILY_UNAVAILABLE",
                        "READ_TEMPORARILY_UNAVAILABLE", null);
        assertThat(History.read(file).operations())
                .extracting(HistoryRecorderTest::result)
                .containsExactly(new Rejected(503), new Unknown(), new ReadFailed(503), new Rejected(503),
                        new Rejected(503), new Rejected(503), new ReadFailed(503), new Unknown());
    }

    @Test
    void recorder_When503CarriesACodeAReadOrWriteMustNotAnswer_RecordsItAsFailAndHistoryReadRejectsIt(
            @TempDir Path directory) throws IOException {
        String writeUnavailable = problem(DegradationErrorConstants.DB_UNAVAILABLE.code());
        String readUnavailable = problem(DegradationErrorConstants.READ_UNAVAILABLE.code());
        String deadlock = problem("DEADLOCK");
        String key = KEY.toString();
        List<Mismatch> mismatches = List.of(
                new Mismatch("READ_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.save("s1", key, () -> new Response(503, readUnavailable))),
                new Mismatch("WRITE_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.read("s2", key, () -> new Response(503, writeUnavailable))),
                new Mismatch("DEADLOCK",
                        recorder -> recorder.read("s2", key, () -> new Response(503, deadlock))),
                new Mismatch("READ_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.delete("s1", key, () -> new Response(503, readUnavailable))),
                new Mismatch("READ_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.unpublish("s1", key, () -> new Response(503, readUnavailable))),
                new Mismatch("WRITE_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.publicRead("anon", key, () -> new Response(503, writeUnavailable))),
                new Mismatch("WRITE_TEMPORARILY_UNAVAILABLE",
                        recorder -> recorder.list("s3", () -> new Response(503, writeUnavailable))));

        for (int index = 0; index < mismatches.size(); index++) {
            Mismatch mismatch = mismatches.get(index);
            Path file = directory.resolve("history-" + index + ".jsonl");
            AtomicLong clock = new AtomicLong();
            try (HistoryRecorder recorder =
                    new HistoryRecorder(Files.newBufferedWriter(file), clock::incrementAndGet)) {
                mismatch.records().accept(recorder);
            }

            String line = Files.readAllLines(file).get(0);
            assertThat(outcome(line)).as(line).isEqualTo("fail");
            assertThat(field(line, "code")).as(line).isEqualTo(mismatch.code());
            assertThatThrownBy(() -> History.read(file)).as(line)
                    .isInstanceOf(HistoryFormatException.class)
                    .hasMessageContaining("without code");
        }
    }

    private record Mismatch(String code, Consumer<HistoryRecorder> records) {
    }

    private static String problem(String code) throws IOException {
        return PROBLEM_MAPPER.writeValueAsString(
                Problems.fill(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE), code, "retry"));
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
        return field(line, "outcome");
    }

    private static String field(String line, String name) {
        return MAPPER.readTree(line).path(name).stringValue(null);
    }
}
