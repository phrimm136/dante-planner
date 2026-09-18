package org.danteplanner.backend.shared.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        } catch (JsonProcessingException e) {
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
