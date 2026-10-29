package org.danteplanner.backend.shared.outbox.service;

import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventPayloadReaderTest {

    private static final long EVENT_ID = 9L;
    private static final String FIELD = "plannerId";
    private static final String MISSING_MESSAGE = "domain event " + EVENT_ID + " carries no " + FIELD;

    private final DomainEventPayloadReader reader = new DomainEventPayloadReader(TestJsonMappers.springDefaults());

    @Test
    void requireId_WhenTheIdIsALong_ReturnsIt() {
        assertThat(reader.requireId(event("{\"plannerId\":42}"), FIELD)).isEqualTo(42L);
    }

    @Test
    void requireId_WhenTheIdIsFractional_Throws() {
        assertThatThrownBy(() -> reader.requireId(event("{\"plannerId\":5.5}"), FIELD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MISSING_MESSAGE);
    }

    @Test
    void requireId_WhenTheFieldIsMissing_Throws() {
        assertThatThrownBy(() -> reader.requireId(event("{}"), FIELD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MISSING_MESSAGE);
    }

    @Test
    void requireId_WhenTheIdIsNotANumber_Throws() {
        assertThatThrownBy(() -> reader.requireId(event("{\"plannerId\":\"42x\"}"), FIELD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MISSING_MESSAGE);
    }

    private static DomainEvent event(String payload) {
        DomainEvent event = DomainEvent.of(DomainEventType.COMMENT_RECEIVED, UUID.randomUUID(), payload);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }
}
