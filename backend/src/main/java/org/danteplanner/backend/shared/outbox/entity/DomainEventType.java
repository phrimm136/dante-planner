package org.danteplanner.backend.shared.outbox.entity;

import java.util.Arrays;
import java.util.List;

/**
 * The constant is stored as text in {@code domain_events.event_type}, so a renamed constant
 * orphans every row already written under the old name.
 */
public enum DomainEventType {
    PLANNER_PUBLISHED,
    PLANNER_RECOMMENDED,
    COMMENT_RECEIVED,
    REPLY_RECEIVED;

    public static List<String> dispatchedNames() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
