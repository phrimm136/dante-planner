package org.danteplanner.backend.integration.consistency;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.Changed;
import org.danteplanner.consistency.ConsistencyChecker;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Rejected;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Shrinker;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;
import org.danteplanner.consistency.Verdict;
import org.danteplanner.consistency.Violation;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper PROBLEM_MAPPER = Jackson2ObjectMapperBuilder.json().build();
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
        assertThat(original.operations()).hasSize(19);
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

    @Test
    void writer_WhenAReadAndAPublicReadFailedWith503_WritesTheReadCodeAndReadsBackEqual(@TempDir Path directory)
            throws IOException {
        History original = History.of(List.of(
                new Read("s1", K1, 1, 2, new ReadFailed(503)),
                new PublicRead("anon", K1, 3, 4, new ReadFailed(503))));
        Path written = directory.resolve("seed-1.min.jsonl");

        HistoryWriter.write(original, written);

        assertThat(History.read(written)).isEqualTo(original);
        assertThat(lines(written)).extracting(line -> line.path("code").textValue())
                .containsExactly(DegradationErrorConstants.READ_UNAVAILABLE.code(),
                        DegradationErrorConstants.READ_UNAVAILABLE.code());
    }

    @Test
    void writer_WhenAHistoryHoldsOneLineOfEveryKindAndFailure_WritesLinesThatReadBackEqual(@TempDir Path directory)
            throws IOException {
        History original = History.of(List.of(
                new Save("s1", K1, 1, 2, new Applied(1)),
                new Save("s1", K1, 3, 4, new Rejected(409)),
                new Save("s1", K1, 5, 6, new Rejected(503)),
                new Save("s1", K1, 7, 8, new Unknown()),
                new Delete("s1", K1, 9, 10, new Deleted()),
                new Delete("s1", K1, 11, 12, new Rejected(503)),
                new Read("s1", K1, 13, 14, new Found(1)),
                new Read("s1", K1, 15, 16, new NotFound()),
                new Read("s1", K1, 17, 18, new ReadFailed(429)),
                new Read("s1", K1, 19, 20, new ReadFailed(503)),
                new Read("s1", K1, 21, 22, new ReadFailed(0)),
                new ListRead("s1", 23, 24, List.of(K1, K2)),
                new Publish("s1", K1, 25, 26, new Changed()),
                new Publish("s1", K1, 27, 28, new Rejected(503)),
                new Unpublish("s1", K1, 29, 30, new Rejected(400)),
                new Unpublish("s1", K1, 31, 32, new Unknown()),
                new PublicRead("anon", K1, 33, 34, new Found(1)),
                new PublicRead("anon", K1, 35, 36, new ReadFailed(503)),
                new PublicRead("anon", K1, 37, 38, new ReadFailed(0))));
        Path written = directory.resolve("seed-1.min.jsonl");

        HistoryWriter.write(original, written);

        assertThat(History.read(written)).isEqualTo(original);
        assertThat(lines(written))
                .filteredOn(line -> "fail".equals(line.path("outcome").textValue())
                        && line.path("status").intValue() == HttpStatus.SERVICE_UNAVAILABLE.value())
                .extracting(line -> line.path("kind").textValue() + ":" + line.path("code").textValue())
                .containsExactly(
                        "save:" + DegradationErrorConstants.DB_UNAVAILABLE.code(),
                        "delete:" + DegradationErrorConstants.DB_UNAVAILABLE.code(),
                        "read:" + DegradationErrorConstants.READ_UNAVAILABLE.code(),
                        "publish:" + DegradationErrorConstants.DB_UNAVAILABLE.code(),
                        "public-read:" + DegradationErrorConstants.READ_UNAVAILABLE.code());
    }

    private static List<JsonNode> lines(Path file) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            lines.add(MAPPER.readTree(line));
        }
        return lines;
    }

    private static Path record(Path file) throws IOException {
        AtomicLong clock = new AtomicLong();
        String version1 = MAPPER.writeValueAsString(PlannerResponse.builder().syncVersion(1).build());
        String version2 = MAPPER.writeValueAsString(PlannerResponse.builder().syncVersion(2).build());
        String publicView = MAPPER.writeValueAsString(PublishedPlannerDetailResponse.builder().syncVersion(1).build());
        String writeUnavailable = PROBLEM_MAPPER.writeValueAsString(Problems.fill(
                ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                DegradationErrorConstants.DB_UNAVAILABLE.code(), DegradationErrorConstants.DB_UNAVAILABLE.message()));
        String readUnavailable = PROBLEM_MAPPER.writeValueAsString(Problems.fill(
                ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                DegradationErrorConstants.READ_UNAVAILABLE.code(), DegradationErrorConstants.READ_UNAVAILABLE.message()));
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
            recorder.save("s2", K1, () -> new Response(503, writeUnavailable));
            recorder.read("s3", K1, () -> new Response(503, readUnavailable));
        }
        return file;
    }
}
