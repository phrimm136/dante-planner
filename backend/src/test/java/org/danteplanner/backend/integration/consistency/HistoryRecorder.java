package org.danteplanner.backend.integration.consistency;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.ThreadRead;

public final class HistoryRecorder implements Closeable {

    static final String NEW_PUBLICATIONS_FLAG = "notifyNewPublications";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OK = "ok";
    private static final String FAIL = "fail";
    private static final String UNKNOWN = "unknown";
    private static final String CODE = "code";
    private static final String WRITE_UNAVAILABLE_CODE = DegradationErrorConstants.DB_UNAVAILABLE.code();
    private static final String READ_UNAVAILABLE_CODE = DegradationErrorConstants.READ_UNAVAILABLE.code();
    private static final Pattern REVISION = Pattern.compile("^\\S+ r(\\d+)$");

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

    Optional<Response> list(String session, ListRead.Listing listing, Exchange exchange) {
        return record(line(session, "list").put("list", listName(listing)), exchange, HistoryRecorder::listOutcome);
    }

    Optional<Response> threadRead(String session, String key, ThreadRead.Audience audience, Exchange exchange) {
        return record(line(session, "thread-read").put("key", key)
                .put("audience", HistoryWriter.audienceName(audience)), exchange, HistoryRecorder::threadOutcome);
    }

    Optional<Response> comment(String session, String key, String comment, long revision, Exchange exchange) {
        return record(line(session, "comment").put("key", key).put("comment", comment).put("revision", revision),
                exchange, HistoryRecorder::commentOutcome);
    }

    Optional<Response> settingsSave(String session, boolean value, Exchange exchange) {
        return record(line(session, "settings-save").put("value", value), exchange,
                (line, response) -> visibilityOutcome("settings-save", line, response));
    }

    Optional<Response> settingsRead(String session, Exchange exchange) {
        return record(line(session, "settings-read"), exchange, HistoryRecorder::settingsOutcome);
    }

    Optional<Response> deactivate(String session, Exchange exchange) {
        return record(line(session, "deactivate"), exchange,
                (line, response) -> visibilityOutcome("deactivate", line, response));
    }

    void reactivate(String session, InProcessChange change) {
        ObjectNode line = line(session, "reactivate");
        long invokedAt = clock.getAsLong();
        String outcome;
        try {
            change.run();
            outcome = OK;
        } catch (Exception e) {
            outcome = UNKNOWN;
        }
        write(line.put("invokedAt", invokedAt).put("completedAt", clock.getAsLong()).put("outcome", outcome));
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
            serverError(line, response, WRITE_UNAVAILABLE_CODE);
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
            serverError(line, response, WRITE_UNAVAILABLE_CODE);
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
            serverError(line, response, WRITE_UNAVAILABLE_CODE);
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
            serverError(line, response, READ_UNAVAILABLE_CODE);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static void commentOutcome(ObjectNode line, Response response) {
        HttpStatusCode status = HttpStatusCode.valueOf(response.status());
        if (status.is2xxSuccessful()) {
            outcome(line, OK, response);
            if (line.path("comment").stringValue().isEmpty()) {
                line.put("comment", response.id());
            }
        } else if (status.is4xxClientError()) {
            outcome(line, FAIL, response);
        } else if (status.is5xxServerError()) {
            serverError(line, response, WRITE_UNAVAILABLE_CODE);
        } else {
            throw unexpected("comment", response);
        }
    }

    private static void settingsOutcome(ObjectNode line, Response response) {
        if (response.status() == HttpStatus.OK.value()) {
            outcome(line, OK, response).put("value", response.flag(NEW_PUBLICATIONS_FLAG));
        } else if (HttpStatusCode.valueOf(response.status()).is5xxServerError()) {
            serverError(line, response, READ_UNAVAILABLE_CODE);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static void threadOutcome(ObjectNode line, Response response) {
        if (response.status() == HttpStatus.OK.value()) {
            ObjectNode comments = outcome(line, OK, response).putObject("comments");
            ArrayNode masked = line.putArray("masked");
            for (Response.ThreadComment comment : response.threadComments()) {
                Matcher revision = REVISION.matcher(comment.content());
                if (!comment.deleted() && revision.matches()) {
                    comments.put(comment.id(), Long.parseLong(revision.group(1)));
                } else {
                    masked.add(comment.id());
                }
            }
        } else if (response.status() == HttpStatus.NOT_FOUND.value()) {
            outcome(line, OK, response);
        } else if (HttpStatusCode.valueOf(response.status()).is5xxServerError()) {
            serverError(line, response, READ_UNAVAILABLE_CODE);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static String listName(ListRead.Listing listing) {
        return switch (listing) {
            case OWNER -> "owner";
            case CATALOG -> "catalog";
        };
    }

    private static void listOutcome(ObjectNode line, Response response) {
        if (response.status() == HttpStatus.OK.value()) {
            Response.PageFacts page = response.pageFacts();
            ArrayNode keys = outcome(line, OK, response).put("page", page.number()).put("size", page.size())
                    .put("totalElements", page.totalElements()).putArray("keys");
            response.plannerIds().forEach(keys::add);
        } else if (HttpStatusCode.valueOf(response.status()).is5xxServerError()) {
            serverError(line, response, READ_UNAVAILABLE_CODE);
        } else {
            outcome(line, FAIL, response);
        }
    }

    private static void serverError(ObjectNode line, Response response, String unavailableCode) {
        Optional<String> code = response.status() == HttpStatus.SERVICE_UNAVAILABLE.value()
                ? response.code().filter(observed -> isRecordedAsFailure(observed, unavailableCode))
                : Optional.empty();
        if (code.isPresent()) {
            outcome(line, FAIL, response).put(CODE, code.get());
        } else {
            outcome(line, UNKNOWN, response);
        }
    }

    private static boolean isRecordedAsFailure(String observed, String unavailableCode) {
        return READ_UNAVAILABLE_CODE.equals(unavailableCode)
                || observed.equals(unavailableCode)
                || observed.equals(READ_UNAVAILABLE_CODE);
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
        } catch (JacksonException e) {
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

    @FunctionalInterface
    interface InProcessChange {

        void run() throws Exception;
    }
}
