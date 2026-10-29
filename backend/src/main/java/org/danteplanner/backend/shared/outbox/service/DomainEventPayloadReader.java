package org.danteplanner.backend.shared.outbox.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DomainEventPayloadReader {

    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private final ObjectMapper objectMapper;

    public long requireId(DomainEvent event, String field) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.getPayload());
        } catch (JacksonException e) {
            throw new IllegalStateException("unreadable payload on domain event " + event.getId(), e);
        }
        JsonNode value = payload.get(field);
        if (value == null || !value.isNumber() || !fitsLong(value.decimalValue())) {
            throw new IllegalStateException(
                    "domain event " + event.getId() + " carries no " + field);
        }
        return value.asLong();
    }

    private static boolean fitsLong(BigDecimal value) {
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }
}
