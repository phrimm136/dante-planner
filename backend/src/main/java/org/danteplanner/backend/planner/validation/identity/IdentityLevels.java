package org.danteplanner.backend.planner.validation.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class IdentityLevels {

    private static final String EQUIPMENT = "equipment";
    private static final String IDENTITY = "identity";
    private static final String LEVEL = "level";

    private IdentityLevels() {
    }

    public static JsonNode clip(JsonNode content, int cap) {
        if (!content.isObject() || identities(content).noneMatch(identity -> rewritten(identity, cap).isPresent())) {
            return content;
        }
        ObjectNode clipped = ((ObjectNode) content).deepCopy();
        identities(clipped).forEach(identity -> rewritten(identity, cap)
                .ifPresent(level -> ((ObjectNode) identity).set(LEVEL, level)));
        return clipped;
    }

    private static Stream<JsonNode> identities(JsonNode content) {
        JsonNode equipment = content.path(EQUIPMENT);
        if (!equipment.isObject()) {
            return Stream.empty();
        }
        return StreamSupport.stream(equipment.spliterator(), false)
                .map(slot -> slot.path(IDENTITY))
                .filter(JsonNode::isObject);
    }

    private static Optional<JsonNode> rewritten(JsonNode identity, int cap) {
        JsonNode level = identity.path(LEVEL);
        if (!level.isNumber() || (level.isDouble() || level.isFloat()) && !Double.isFinite(level.doubleValue())) {
            return Optional.empty();
        }
        BigDecimal value = level.decimalValue();
        if (value.stripTrailingZeros().scale() > 0) {
            return Optional.empty();
        }
        if (value.compareTo(BigDecimal.valueOf(cap)) > 0) {
            return Optional.of(IntNode.valueOf(cap));
        }
        return level.isIntegralNumber() ? Optional.empty() : Optional.of(integer(value.toBigIntegerExact()));
    }

    private static JsonNode integer(BigInteger value) {
        if (value.bitLength() < Integer.SIZE) {
            return IntNode.valueOf(value.intValue());
        }
        return value.bitLength() < Long.SIZE ? LongNode.valueOf(value.longValue()) : BigIntegerNode.valueOf(value);
    }
}
