package org.danteplanner.backend.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorded;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchConsumedEventTest {

    @Mock
    private DomainEventRepository events;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void dispatchedNames_WhenListed_LeaveOutOnlyTheBatchConsumedTypes() {
        assertThat(DomainEventType.dispatchedNames())
                .containsExactlyInAnyOrder("PLANNER_PUBLISHED", "PLANNER_RECOMMENDED", "COMMENT_RECEIVED",
                        "REPLY_RECEIVED");
        assertThat(DomainEventType.PLANNER_CATALOG_CHANGED.isBatchConsumed()).isTrue();
    }

    @Test
    void recordDomainEvent_WhenTheTypeIsBatchConsumed_HandsNothingToTheEagerHop() {
        when(events.insert(any(DomainEvent.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        recorder().recordDomainEvent(DomainEventType.PLANNER_CATALOG_CHANGED, UUID.randomUUID(), Map.of());

        verify(events).insert(any(DomainEvent.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void recordDomainEvent_WhenTheTypeIsDispatched_HandsTheRowToTheEagerHop() {
        when(events.insert(any(DomainEvent.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        recorder().recordDomainEvent(DomainEventType.PLANNER_PUBLISHED, UUID.randomUUID(), Map.of());

        verify(eventPublisher).publishEvent(new DomainEventRecorded(41L));
    }

    private DomainEventRecorder recorder() {
        return new DomainEventRecorder(events, new ObjectMapper(), eventPublisher);
    }

    private static DomainEvent withId(DomainEvent event) {
        ReflectionTestUtils.setField(event, "id", 41L);
        return event;
    }
}
