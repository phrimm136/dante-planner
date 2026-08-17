package org.danteplanner.backend.shared.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Records the observer effect a write owes, inside the transaction of that write.
 *
 * <p>{@code MANDATORY} rather than {@code REQUIRED}: an event recorded in a transaction of its own
 * would survive a rollback of the write that occasioned it, and the whole point of the row is that
 * it commits with its cause or not at all.</p>
 */
@Service
@RequiredArgsConstructor
public class DomainEventRecorder {

    private final DomainEventRepository events;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Record one event and arm the eager dispatch that follows the caller's commit.
     *
     * @param type        the kind of effect owed
     * @param aggregateId the aggregate the effect is about
     * @param payload     the ids the effect needs to re-read its subject
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDomainEvent(DomainEventType type, UUID aggregateId, Map<String, Object> payload) {
        DomainEvent saved = events.insert(DomainEvent.of(type, aggregateId, serializePayload(payload)));
        eventPublisher.publishEvent(new DomainEventRecorded(saved.getId()));
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("domain event payload is not serializable", e);
        }
    }
}
