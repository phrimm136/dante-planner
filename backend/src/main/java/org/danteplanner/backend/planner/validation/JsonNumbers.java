package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

public final class JsonNumbers {

    private JsonNumbers() {
    }

    public static Optional<Integer> intValue(JsonNode node) {
        return switch (node.getNodeType()) {
            case NUMBER -> integralValue(node)
                    .filter(value -> value.bitLength() < Integer.SIZE)
                    .map(BigInteger::intValue);
            default -> Optional.empty();
        };
    }

    public static Optional<BigInteger> integralValue(JsonNode number) {
        return switch (number.numberType()) {
            case INT, LONG, BIG_INTEGER -> Optional.of(number.bigIntegerValue());
            case FLOAT, DOUBLE, BIG_DECIMAL -> exactIntegralValue(number);
        };
    }

    private static Optional<BigInteger> exactIntegralValue(JsonNode fraction) {
        return Optional.of(fraction)
                .filter(number -> Double.isFinite(number.doubleValue()))
                .map(JsonNode::decimalValue)
                .filter(decimal -> decimal.stripTrailingZeros().scale() <= 0)
                .map(BigDecimal::toBigInteger);
    }
}
