package org.danteplanner.backend.integration.consistency;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.consistency.ConsistencyChecker;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.Shrinker;
import org.danteplanner.consistency.Verdict;
import org.danteplanner.consistency.Violation;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String K1 = UUID.randomUUID().toString();
    private static final String K2 = UUID.randomUUID().toString();

    @Test
    void writer_WhenARecordedHistoryHoldsEveryKindAndOutcome_WritesLinesThatReadBackEqual(@TempDir Path directory)
            throws IOException {
        Path recorded = record(directory.resolve("seed-1.jsonl"));
        Path written = directory.resolve("seed-1.min.jsonl");
        History original = History.read(recorded);

        HistoryWriter.write(original, written);

        assertThat(History.read(written)).isEqualTo(original);
        assertThat(original.operations()).hasSize(17);
    }

    @Test
    void shrunkHistory_WhenWrittenBesideAHistoryWithOnePlantedViolation_ReadsBackWithThatViolation(
            @TempDir Path directory) throws IOException {
        History original = History.read(record(directory.resolve("seed-1.jsonl")));
        Verdict verdict = ConsistencyChecker.check(original);
        assertThat(verdict.violations()).extracting(Violation::rule).containsExactly("author-read-your-writes");
        Path minimal = directory.resolve("seed-1.min.jsonl");

        HistoryWriter.write(Shrinker.shrink(original, verdict.violations().get(0)), minimal);

        History shrunk = History.read(minimal);
        assertThat(ConsistencyChecker.check(shrunk).violations()).contains(verdict.violations().get(0));
        assertThat(shrunk.operations()).hasSizeLessThan(original.operations().size());
    }

    private static Path record(Path file) throws IOException {
        AtomicLong clock = new AtomicLong();
        String version1 = MAPPER.writeValueAsString(PlannerResponse.builder().syncVersion(1).build());
        String version2 = MAPPER.writeValueAsString(PlannerResponse.builder().syncVersion(2).build());
        String publicView = MAPPER.writeValueAsString(PublishedPlannerDetailResponse.builder().syncVersion(1).build());
        String page = "{\"content\":[{\"id\":\"" + K1 + "\"},{\"id\":\"" + K2 + "\"}]}";
        try (HistoryRecorder recorder = new HistoryRecorder(Files.newBufferedWriter(file), clock::incrementAndGet)) {
            recorder.save("s1", K1, () -> new Response(201, version1));
            recorder.save("s1", K2, () -> new Response(201, version1));
            recorder.save("s1", K1, () -> new Response(200, version2));
            recorder.save("s2", K2, () -> new Response(409, ""));
            recorder.save("s3", K2, () -> new Response(503, ""));
            recorder.list("s2", () -> new Response(200, page));
            recorder.list("s3", () -> new Response(503, ""));
            recorder.publish("s2", K2, () -> new Response(200, version1));
            recorder.publish("s3", K2, () -> new Response(400, ""));
            recorder.unpublish("s3", K2, () -> {
                throw new SocketException("connection reset");
            });
            recorder.publicRead("anon", K2, () -> new Response(200, publicView));
            recorder.publicRead("anon", K1, () -> new Response(404, ""));
            recorder.publicRead("anon", K1, () -> new Response(429, ""));
            recorder.read("s1", K1, () -> new Response(200, version1));
            recorder.read("s2", K2, () -> new Response(503, ""));
            recorder.delete("s1", K2, () -> new Response(204, ""));
            recorder.delete("s1", K2, () -> new Response(404, ""));
            recorder.read("s3", K2, () -> new Response(404, ""));
        }
        return file;
    }
}
