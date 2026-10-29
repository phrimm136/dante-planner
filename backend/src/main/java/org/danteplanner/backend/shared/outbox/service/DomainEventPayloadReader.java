package org.danteplanner.backend.shared.outbox.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventPayloadReader {

    private final ObjectMapper objectMapper;

    public long requireId(DomainEvent event, String field) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.getPayload());
        } catch (JacksonException e) {
            throw new IllegalStateException("unreadable payload on domain event " + event.getId(), e);
        }
        JsonNode value = payload.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalStateException(
                    "domain event " + event.getId() + " carries no " + field);
        }
        return value.asLong();
    }
}
