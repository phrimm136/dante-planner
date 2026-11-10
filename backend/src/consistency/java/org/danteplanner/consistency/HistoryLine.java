package org.danteplanner.consistency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
        } catch (JacksonException e) {
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
            case "thread-read" ->
                    Optional.of(new ThreadRead(session, text("key"), audience(), invokedAt, completedAt,
                            threadResult(outcome)));
            case "deactivate" ->
                    Optional.of(new Deactivate(session, invokedAt, completedAt, visibilityResult(kind, outcome)));
            case "reactivate" -> Optional.of(new Reactivate(session, invokedAt, completedAt, inProcessResult(outcome)));
            case "comment" -> Optional.of(commentWrite(session, invokedAt, completedAt, outcome));
            case "settings-save" -> Optional.of(
                    new SettingsSave(session, bool("value"), invokedAt, completedAt, visibilityResult(kind, outcome)));
            case "settings-read" ->
                    Optional.of(new SettingsRead(session, invokedAt, completedAt, settingsResult(outcome)));
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

    private CommentWrite commentWrite(String session, long invokedAt, long completedAt, String outcome) {
        VisibilityResult result = switch (outcome) {
            case "ok" -> {
                statusOfClass(SUCCESS_CLASS);
                yield new Changed();
            }
            case "fail" -> new Rejected(rejectedStatus());
            case "unknown" -> {
                requireNoResponseOrServerError();
                yield new Unknown();
            }
            default -> throw unknownOutcome(outcome);
        };
        String comment = text("comment");
        if (result instanceof Changed && comment.isEmpty()) {
            throw failure("an applied comment write without a comment id");
        }
        long revision = integral("revision");
        if (revision < 1) {
            throw failure("comment revision " + revision + " is below 1");
        }
        return new CommentWrite(session, text("key"), comment, revision, invokedAt, completedAt, result);
    }

    private SettingsResult settingsResult(String outcome) {
        if ("ok".equals(outcome)) {
            int status = status();
            if (status != OK) {
                throw failure("settings read ok with status " + status);
            }
            return new Seen(bool("value"));
        }
        return switch (readResult(outcome)) {
            case Found found -> throw failure("a settings read carries no syncVersion");
            case NotFound notFound -> throw failure("a settings read is never not found");
            case ReadFailed failed -> failed;
        };
    }

    private VisibilityResult inProcessResult(String outcome) {
        if (node.has("status")) {
            throw failure("an in-process change carries no status");
        }
        return switch (outcome) {
            case "ok" -> new Changed();
            case "unknown" -> new Unknown();
            default -> throw unknownOutcome(outcome);
        };
    }

    private ThreadResult threadResult(String outcome) {
        if ("ok".equals(outcome) && status() == OK) {
            return new Shown(revisions(), masked());
        }
        return switch (readResult(outcome)) {
            case Found found -> throw failure("a thread read carries no syncVersion");
            case NotFound notFound -> notFound;
            case ReadFailed failed -> failed;
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
            case "ok" -> Optional.of(new ListRead(session, listing(), invokedAt, completedAt, intField("page"),
                    intField("size"), integral("totalElements"), keys()));
            case "fail" -> {
                if (node.path("status").asInt(0) == SERVICE_UNAVAILABLE) {
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
        if (!code.equals(node.path("code").stringValue(null))) {
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
        if (!value.isString()) {
            throw failure("missing or non-string " + field);
        }
        return value.stringValue();
    }

    private long integral(String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw failure("missing or non-long " + field);
        }
        return value.longValue();
    }

    private ListRead.Listing listing() {
        return switch (text("list")) {
            case "owner" -> ListRead.Listing.OWNER;
            case "catalog" -> ListRead.Listing.CATALOG;
            default -> throw failure("unknown list " + text("list"));
        };
    }

    private ThreadRead.Audience audience() {
        return switch (text("audience")) {
            case "owner" -> ThreadRead.Audience.OWNER;
            case "public" -> ThreadRead.Audience.PUBLIC;
            default -> throw failure("unknown audience " + text("audience"));
        };
    }

    private int intField(String field) {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.intValue() < 0) {
            throw failure("missing or negative " + field);
        }
        return value.intValue();
    }

    private List<String> keys() {
        JsonNode value = node.path("keys");
        if (!value.isArray()) {
            throw failure("missing or non-array keys");
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode key : value) {
            if (!key.isString()) {
                throw failure("non-string entry in keys");
            }
            keys.add(key.stringValue());
        }
        return keys;
    }

    private Map<String, Long> revisions() {
        JsonNode value = node.path("comments");
        if (!value.isObject()) {
            throw failure("missing or non-object comments");
        }
        Map<String, Long> revisions = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            JsonNode revision = entry.getValue();
            if (!revision.isIntegralNumber() || !revision.canConvertToLong()) {
                throw failure("non-long revision for comment " + entry.getKey());
            }
            revisions.put(entry.getKey(), revision.longValue());
        }
        return revisions;
    }

    private Set<String> masked() {
        JsonNode value = node.path("masked");
        if (!value.isArray()) {
            throw failure("missing or non-array masked");
        }
        Set<String> masked = new HashSet<>();
        for (JsonNode comment : value) {
            if (!comment.isString()) {
                throw failure("non-string entry in masked");
            }
            masked.add(comment.stringValue());
        }
        return masked;
    }

    private boolean bool(String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) {
            throw failure("missing or non-boolean " + field);
        }
        return value.booleanValue();
    }

    private HistoryFormatException unknownOutcome(String outcome) {
        return failure("unknown outcome " + outcome);
    }

    private HistoryFormatException failure(String reason) {
        return new HistoryFormatException(number, reason);
    }
}
