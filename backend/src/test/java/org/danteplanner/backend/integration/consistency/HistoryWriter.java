package org.danteplanner.backend.integration.consistency;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.Changed;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.DeleteResult;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.History;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.ReadResult;
import org.danteplanner.consistency.Rejected;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.SaveResult;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;
import org.danteplanner.consistency.VisibilityResult;

public final class HistoryWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OK = "ok";
    private static final String FAIL = "fail";
    private static final String UNKNOWN = "unknown";
    private static final int NO_RESPONSE = 0;
    private static final String WRITE_UNAVAILABLE_CODE = DegradationErrorConstants.DB_UNAVAILABLE.code();
    private static final String READ_UNAVAILABLE_CODE = DegradationErrorConstants.READ_UNAVAILABLE.code();

    private HistoryWriter() {
    }

    public static void write(History history, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            for (Operation operation : history.operations()) {
                writer.write(MAPPER.writeValueAsString(line(operation)));
                writer.write('\n');
            }
        }
    }

    private static ObjectNode line(Operation operation) {
        return switch (operation) {
            case Save save -> saveResult(
                    keyed(save.session(), "save", save.key(), save.invokedAt(), save.completedAt()), save.result());
            case Delete delete -> deleteResult(
                    keyed(delete.session(), "delete", delete.key(), delete.invokedAt(), delete.completedAt()),
                    delete.result());
            case Read read -> readResult(
                    keyed(read.session(), "read", read.key(), read.invokedAt(), read.completedAt()), read.result());
            case ListRead list -> listResult(timed(list.session(), "list", list.invokedAt(), list.completedAt()),
                    list);
            case Publish publish -> visibilityResult(
                    keyed(publish.session(), "publish", publish.key(), publish.invokedAt(), publish.completedAt()),
                    publish.result());
            case Unpublish unpublish -> visibilityResult(
                    keyed(unpublish.session(), "unpublish", unpublish.key(), unpublish.invokedAt(),
                            unpublish.completedAt()),
                    unpublish.result());
            case PublicRead read -> readResult(
                    keyed(read.session(), "public-read", read.key(), read.invokedAt(), read.completedAt()),
                    read.result());
        };
    }

    private static ObjectNode saveResult(ObjectNode line, SaveResult result) {
        return switch (result) {
            case Applied applied -> outcome(line, OK, HttpStatus.OK.value()).put("syncVersion", applied.syncVersion());
            case Rejected rejected -> failed(line, rejected.status(), WRITE_UNAVAILABLE_CODE);
            case Unknown unknown -> line.put("outcome", UNKNOWN);
        };
    }

    private static ObjectNode deleteResult(ObjectNode line, DeleteResult result) {
        return switch (result) {
            case Deleted deleted -> outcome(line, OK, HttpStatus.NO_CONTENT.value());
            case Rejected rejected -> failed(line, rejected.status(), WRITE_UNAVAILABLE_CODE);
            case Unknown unknown -> line.put("outcome", UNKNOWN);
        };
    }

    private static ObjectNode visibilityResult(ObjectNode line, VisibilityResult result) {
        return switch (result) {
            case Changed changed -> outcome(line, OK, HttpStatus.OK.value());
            case Rejected rejected -> failed(line, rejected.status(), WRITE_UNAVAILABLE_CODE);
            case Unknown unknown -> line.put("outcome", UNKNOWN);
        };
    }

    private static ObjectNode readResult(ObjectNode line, ReadResult result) {
        return switch (result) {
            case Found found -> outcome(line, OK, HttpStatus.OK.value()).put("syncVersion", found.syncVersion());
            case NotFound notFound -> outcome(line, OK, HttpStatus.NOT_FOUND.value());
            case ReadFailed failed -> failed.status() == NO_RESPONSE
                    ? line.put("outcome", UNKNOWN)
                    : failed(line, failed.status(), READ_UNAVAILABLE_CODE);
        };
    }

    private static ObjectNode listResult(ObjectNode line, ListRead list) {
        ArrayNode keys = outcome(line, OK, HttpStatus.OK.value()).putArray("keys");
        list.keys().forEach(keys::add);
        return line;
    }

    private static ObjectNode failed(ObjectNode line, int status, String unavailableCode) {
        outcome(line, FAIL, status);
        return status == HttpStatus.SERVICE_UNAVAILABLE.value()
                ? line.put("code", unavailableCode)
                : line;
    }

    private static ObjectNode outcome(ObjectNode line, String outcome, int status) {
        return line.put("outcome", outcome).put("status", status);
    }

    private static ObjectNode keyed(String session, String kind, String key, long invokedAt, long completedAt) {
        return MAPPER.createObjectNode().put("session", session).put("kind", kind).put("key", key)
                .put("invokedAt", invokedAt).put("completedAt", completedAt);
    }

    private static ObjectNode timed(String session, String kind, long invokedAt, long completedAt) {
        return MAPPER.createObjectNode().put("session", session).put("kind", kind)
                .put("invokedAt", invokedAt).put("completedAt", completedAt);
    }
}
