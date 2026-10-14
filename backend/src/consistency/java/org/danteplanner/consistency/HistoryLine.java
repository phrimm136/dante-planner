package org.danteplanner.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class HistoryLine {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final String WRITE_UNAVAILABLE_CODE = "WRITE_TEMPORARILY_UNAVAILABLE";
    private static final String READ_UNAVAILABLE_CODE = "READ_TEMPORARILY_UNAVAILABLE";
    private static final int SUCCESS_CLASS = 2;
    private static final int CLIENT_ERROR_CLASS = 4;
    private static final int SERVER_ERROR_CLASS = 5;
    private static final int NO_RESPONSE = 0;
    private static final int STATUS_CLASS_DIVISOR = 100;

    private final JsonNode node;
    private final int number;

    private HistoryLine(JsonNode node, int number) {
        this.node = node;
        this.number = number;
    }

    static Optional<Operation> parse(String text, int number) {
        return new HistoryLine(tree(text, number), number).operation();
    }

    private static JsonNode tree(String text, int number) {
        JsonNode node;
        try {
            node = MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new HistoryFormatException(number, "invalid JSON", e);
        }
        if (!node.isObject()) {
            throw new HistoryFormatException(number, "not a JSON object");
        }
        return node;
    }

    private Optional<Operation> operation() {
        String session = text("session");
        String kind = text("kind");
        long invokedAt = integral("invokedAt");
        long completedAt = integral("completedAt");
        if (completedAt < invokedAt) {
            throw failure("completedAt " + completedAt + " is before invokedAt " + invokedAt);
        }
        String outcome = text("outcome");
        return switch (kind) {
            case "save" -> Optional.of(new Save(session, text("key"), invokedAt, completedAt, saveResult(outcome)));
            case "delete" ->
                    Optional.of(new Delete(session, text("key"), invokedAt, completedAt, deleteResult(outcome)));
            case "read" -> Optional.of(new Read(session, text("key"), invokedAt, completedAt, readResult(outcome)));
            case "list" -> listRead(session, invokedAt, completedAt, outcome);
            case "publish" -> Optional.of(
                    new Publish(session, text("key"), invokedAt, completedAt, visibilityResult(kind, outcome)));
            case "unpublish" -> Optional.of(
                    new Unpublish(session, text("key"), invokedAt, completedAt, visibilityResult(kind, outcome)));
            case "public-read" ->
                    Optional.of(new PublicRead(session, text("key"), invokedAt, completedAt, readResult(outcome)));
            default -> throw failure("unknown kind " + kind);
        };
    }

    private SaveResult saveResult(String outcome) {
        return switch (outcome) {
            case "ok" -> {
                statusOfClass(SUCCESS_CLASS);
                yield new Applied(integral("syncVersion"));
            }
            case "fail" -> new Rejected(rejectedStatus());
            case "unknown" -> {
                requireNoResponseOrServerError();
                yield new Unknown();
            }
            default -> throw unknownOutcome(outcome);
        };
    }

    private DeleteResult deleteResult(String outcome) {
        return switch (outcome) {
            case "ok" -> {
                int status = status();
                if (status != NO_CONTENT) {
                    throw failure("delete ok with status " + status);
                }
                yield new Deleted();
            }
            case "fail" -> new Rejected(rejectedStatus());
            case "unknown" -> {
                requireNoResponseOrServerError();
                yield new Unknown();
            }
            default -> throw unknownOutcome(outcome);
        };
    }

    private VisibilityResult visibilityResult(String kind, String outcome) {
        return switch (outcome) {
            case "ok" -> {
                int status = status();
                if (status != OK) {
                    throw failure(kind + " ok with status " + status);
                }
                yield new Changed();
            }
            case "fail" -> new Rejected(rejectedStatus());
            case "unknown" -> {
                requireNoResponseOrServerError();
                yield new Unknown();
            }
            default -> throw unknownOutcome(outcome);
        };
    }

    private ReadResult readResult(String outcome) {
        return switch (outcome) {
            case "ok" -> {
                int status = status();
                if (status == OK) {
                    yield new Found(integral("syncVersion"));
                }
                if (status == NOT_FOUND) {
                    yield new NotFound();
                }
                throw failure("read ok with status " + status);
            }
            case "fail" -> {
                int status = status();
                if (status == OK || status == NOT_FOUND) {
                    throw failure("read fail with status " + status);
                }
                if (status == SERVICE_UNAVAILABLE) {
                    requireUnavailableCode(READ_UNAVAILABLE_CODE);
                }
                yield new ReadFailed(status);
            }
            case "unknown" -> {
                requireNoResponseOrServerError();
                yield new ReadFailed(NO_RESPONSE);
            }
            default -> throw unknownOutcome(outcome);
        };
    }

    private Optional<Operation> listRead(String session, long invokedAt, long completedAt, String outcome) {
        return switch (outcome) {
            case "ok" -> Optional.of(new ListRead(session, invokedAt, completedAt, keys()));
            case "fail" -> {
                if (node.path("status").intValue() == SERVICE_UNAVAILABLE) {
                    requireUnavailableCode(READ_UNAVAILABLE_CODE);
                }
                yield Optional.empty();
            }
            case "unknown" -> Optional.empty();
            default -> throw unknownOutcome(outcome);
        };
    }

    private int rejectedStatus() {
        if (status() == SERVICE_UNAVAILABLE) {
            requireUnavailableCode(WRITE_UNAVAILABLE_CODE);
            return SERVICE_UNAVAILABLE;
        }
        return statusOfClass(CLIENT_ERROR_CLASS);
    }

    private void requireUnavailableCode(String code) {
        if (!code.equals(node.path("code").textValue())) {
            throw failure("fail with status " + SERVICE_UNAVAILABLE + " without code " + code);
        }
    }

    private void requireNoResponseOrServerError() {
        if (node.has("status")) {
            statusOfClass(SERVER_ERROR_CLASS);
        }
    }

    private int statusOfClass(int statusClass) {
        int status = status();
        if (status / STATUS_CLASS_DIVISOR != statusClass) {
            throw failure("status " + status + " is not " + statusClass + "xx");
        }
        return status;
    }

    private int status() {
        JsonNode value = node.path("status");
        if (!value.isInt()) {
            throw failure("missing or non-integer status");
        }
        return value.intValue();
    }

    private String text(String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            throw failure("missing or non-string " + field);
        }
        return value.textValue();
    }

    private long integral(String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw failure("missing or non-long " + field);
        }
        return value.longValue();
    }

    private List<String> keys() {
        JsonNode value = node.path("keys");
        if (!value.isArray()) {
            throw failure("missing or non-array keys");
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode key : value) {
            if (!key.isTextual()) {
                throw failure("non-string entry in keys");
            }
            keys.add(key.textValue());
        }
        return keys;
    }

    private HistoryFormatException unknownOutcome(String outcome) {
        return failure("unknown outcome " + outcome);
    }

    private HistoryFormatException failure(String reason) {
        return new HistoryFormatException(number, reason);
    }
}
