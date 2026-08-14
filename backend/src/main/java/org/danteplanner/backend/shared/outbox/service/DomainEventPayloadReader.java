package org.danteplanner.backend.shared.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.springframework.stereotype.Component;

/**
 * Reads the ids an effect arm needs out of a recorded payload.
 */
@Component
@RequiredArgsConstructor
public class DomainEventPayloadReader {

    private final ObjectMapper objectMapper;

    /**
     * The numeric id stored under a payload field.
     *
     * @param event the event being dispatched
     * @param field the payload field naming the id
     * @return the id
     * @throws IllegalStateException if the payload cannot be parsed or carries no such field
     */
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
