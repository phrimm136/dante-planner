package org.danteplanner.backend.integration.consistency;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class HistoryRecorder implements Closeable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OK = "ok";
    private static final String FAIL = "fail";
    private static final String UNKNOWN = "unknown";

    private final Writer writer;
    private final LongSupplier clock;

    public HistoryRecorder(Writer writer, LongSupplier clock) {
        this.writer = writer;
        this.clock = clock;
    }

    Optional<Response> save(String session, String key, Exchange exchange) {
        return record(line(session, "save").put("key", key), exchange, HistoryRecorder::saveOutcome);
    }

    Optional<Response> delete(String session, String key, Exchange exchange) {
        return record(line(session, "delete").put("key", key), exchange, HistoryRecorder::deleteOutcome);
    }

    Optional<Response> read(String session, String key, Exchange exchange) {
        return record(line(session, "read").put("key", key), exchange, HistoryRecorder::readOutcome);
    }

    Optional<Response> list(String session, Exchange exchange) {
        return record(line(session, "list"), exchange, HistoryRecorder::listOutcome);
    }

    Optional<Response> publish(String session, String key, Exchange exchange) {
        return record(line(session, "publish").put("key", key), exchange,
                (line, response) -> visibilityOutcome("publish", line, response));
    }

    Optional<Response> unpublish(String session, String key, Exchange exchange) {
        return record(line(session, "unpublish").put("key", key), exchange,
                (line, response) -> visibilityOutcome("unpublish", line, response));
    }

    Optional<Response> publicRead(String session, String key, Exchange exchange) {
        return record(line(session, "public-read").put("key", key), exchange, HistoryRecorder::readOutcome);
    }

    private Optional<Response> record(ObjectNode line, Exchange exchange,
            BiConsumer<ObjectNode, Response> outcome) {
        long invokedAt = clock.getAsLong();
        Response response;
        try {
            response = exchange.perform();
        } catch (Exception e) {
            long completedAt = clock.getAsLong();
            write(line.put("invokedAt", invokedAt).put("completedAt", completedAt).put("outcome", UNKNOWN));
            return Optional.empty();
        }
        long completedAt = clock.getAsLong();
        line.put("invokedAt", invokedAt).put("completedAt", completedAt);
        outcome.accept(line, response);
        write(line);
        return Optional.of(response);
    }

    private static void saveOutcome(ObjectNode line, Response response) {
        HttpStatusCode status = HttpStatusCode.valueOf(response.status());
        if (status.is2xxSuccessful()) {
            outcome(line, OK, response).put("syncVersion", response.syncVersion());
        } else if (status.is4xxClientError()) {
            outcome(line, FAIL, response);
        } else if (status.is5xxServerError()) {
            outcome(line, UNKNOWN, response);
        } else {
            throw unexpected("save", response);
        }
    }

    private static void deleteOutcome(ObjectNode line, Response response) {
        HttpStatusCode status = HttpStatusCode.valueOf(response.status());
        if (response.status() == HttpStatus.NO_CONTENT.value()) {
            outcome(line, OK, response);
        } else if (status.is4xxClientError()) {
            outcome(line, FAIL, response);
        } else if (status.is5xxServerError()) {
            outcome(line, UNKNOWN, response);
        } else {
            throw unexpected("delete", response);
        }
    }

    private static void visibilityOutcome(String kind, ObjectNode line, Response response) {
        HttpStatusCode status = HttpStatusCode.valueOf(response.status());
        if (response.status() == HttpStatus.OK.value()) {
            outcome(line, OK, response);
        } else if (status.is4xxClientError()) {
            outcome(line, FAIL, response);
        } else if (status.is5xxServerError()) {
            outcome(line, UNKNOWN, response);
        } else {
            throw unexpected(kind, response);
        }
    }

    private static void readOutcome(ObjectNode line, Response response) {
        if (response.status() == HttpStatus.OK.value()) {
            outcome(line, OK, response).put("syncVersion", response.syncVersion());
        } else if (response.status() == HttpStatus.NOT_FOUND.value()) {
            outcome(line, OK, response);
        } else if (HttpStatusCode.valueOf(response.status()).is5xxServerError()) {
            outcome(line, UNKNOWN, response);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static void listOutcome(ObjectNode line, Response response) {
        if (response.status() == HttpStatus.OK.value()) {
            ArrayNode keys = outcome(line, OK, response).putArray("keys");
            response.plannerIds().forEach(keys::add);
        } else if (HttpStatusCode.valueOf(response.status()).is5xxServerError()) {
            outcome(line, UNKNOWN, response);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static ObjectNode outcome(ObjectNode line, String outcome, Response response) {
        return line.put("outcome", outcome).put("status", response.status());
    }

    private static IllegalStateException unexpected(String kind, Response response) {
        return new IllegalStateException("a " + kind + " answered " + response.status() + ": " + response.body());
    }

    private static ObjectNode line(String session, String kind) {
        return MAPPER.createObjectNode().put("session", session).put("kind", kind);
    }

    private synchronized void write(ObjectNode line) {
        try {
            writer.write(MAPPER.writeValueAsString(line));
            writer.write('\n');
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not render a history line", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }

    @FunctionalInterface
    interface Exchange {

        Response perform() throws Exception;
    }
}
