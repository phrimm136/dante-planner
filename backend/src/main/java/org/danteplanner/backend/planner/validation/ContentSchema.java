package org.danteplanner.backend.planner.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeType;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ContentSchema {

    private record Cursor(ContentPath path, JsonNode schema) {

        Cursor then(Object segment) {
            return switch (segment) {
                case Integer index -> new Cursor(path.index(index), schema.path(ITEMS));
                default -> named(segment.toString());
            };
        }

        private Cursor named(String name) {
            return new Cursor(keyed(schema) ? path.key(name) : path.field(name), propertySchema(schema, name));
        }
    }

    private record Found(ErrorCode code, ContentPath path) {
    }

    private static final String LOCATION = "classpath:planner/planner-content.schema.json";
    private static final String RESOURCE = "/planner/planner-content.schema.json";

    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String PROPERTIES = "properties";
    private static final String ITEMS = "items";
    private static final Pattern NUMERIC_NAME = Pattern.compile("\\d+");
    private static final int MAX_VALUE_LOG_LENGTH = 100;

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private static final ContentSchema PLANNER = new ContentSchema(JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(SchemaLocation.of(LOCATION),
                    SchemaValidatorsConfig.builder()
                            .pathType(PathType.JSON_POINTER)
                            .regularExpressionFactory(new EcmaAnchoredRegexFactory())
                            .build()));

    private final JsonSchema schema;
    private final JsonNode schemaTree;

    private ContentSchema(JsonSchema schema) {
        this.schema = schema;
        this.schemaTree = readSchemaTree();
    }

    private static JsonNode readSchemaTree() {
        return MAPPER.readTree(ContentSchema.class.getResourceAsStream(RESOURCE));
    }

    public static ContentSchema planner() {
        return PLANNER;
    }

    public List<Violation> violations(JsonNode content) {
        Map<Found, Violation> found = schema.validate(content.toString(), InputFormat.JSON).stream()
                .map(message -> violation(message, content))
                .collect(Collectors.toMap(violation -> new Found(violation.code(), violation.path()),
                        Function.identity(), (first, later) -> first, LinkedHashMap::new));
        return found.values().stream().sorted(Violation.BY_PATH_THEN_CODE).toList();
    }

    private Violation violation(ValidationMessage message, JsonNode content) {
        Cursor at = cursor(message.getInstanceLocation());
        return switch (message.getType()) {
            case REQUIRED -> Violation.missingField(at.then(message.getProperty()).path());
            case ADDITIONAL_PROPERTIES -> Violation.unknownField(at.then(message.getProperty()).path());
            default -> invalidValue(at.path(), instanceAt(content, message.getInstanceLocation()));
        };
    }

    private static Violation invalidValue(ContentPath path, JsonNode actual) {
        return new Violation(ErrorCode.INVALID_FIELD_TYPE, path,
                String.format("Field '%s' has an invalid value: %s", path.render(), describe(actual)));
    }

    private static String describe(JsonNode actual) {
        return switch (actual.getNodeType()) {
            case NULL -> "null";
            case STRING -> "string \"" + truncate(actual.asString()) + "\"";
            case NUMBER -> "number " + actual;
            case BOOLEAN -> "boolean " + actual.asBoolean();
            default -> actual.getNodeType().toString().toLowerCase(Locale.ROOT);
        };
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LOG_LENGTH ? value : value.substring(0, MAX_VALUE_LOG_LENGTH) + "…";
    }

    private Cursor cursor(JsonNodePath location) {
        return IntStream.range(0, location.getNameCount())
                .mapToObj(location::getElement)
                .reduce(new Cursor(ContentPath.root(), schemaTree), Cursor::then, (left, right) -> right);
    }

    private static JsonNode instanceAt(JsonNode content, JsonNodePath location) {
        return IntStream.range(0, location.getNameCount())
                .mapToObj(location::getElement)
                .reduce(content, ContentSchema::child, (left, right) -> right);
    }

    private static JsonNode child(JsonNode node, Object segment) {
        return switch (segment) {
            case Integer index -> node.path(index);
            default -> node.path(segment.toString());
        };
    }

    private static boolean keyed(JsonNode objectSchema) {
        JsonNode properties = objectSchema.path(PROPERTIES);
        return switch (properties.getNodeType()) {
            case OBJECT -> properties.properties().stream()
                    .allMatch(property -> NUMERIC_NAME.matcher(property.getKey()).matches());
            default -> objectSchema.path(ADDITIONAL_PROPERTIES).getNodeType() == JsonNodeType.OBJECT;
        };
    }

    private static JsonNode propertySchema(JsonNode objectSchema, String name) {
        JsonNode declared = objectSchema.path(PROPERTIES).path(name);
        return switch (declared.getNodeType()) {
            case MISSING -> objectSchema.path(ADDITIONAL_PROPERTIES);
            default -> declared;
        };
    }
}
