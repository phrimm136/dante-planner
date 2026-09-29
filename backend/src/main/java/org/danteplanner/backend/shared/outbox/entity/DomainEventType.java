package org.danteplanner.backend.shared.outbox.entity;

import java.util.Arrays;
import java.util.List;

/**
 * The constant is stored as text in {@code domain_events.event_type}, so a renamed constant
 * orphans every row already written under the old name.
 */
public enum DomainEventType {
    PLANNER_PUBLISHED(false),
    PLANNER_RECOMMENDED(false),
    COMMENT_RECEIVED(false),
    REPLY_RECEIVED(false),
    PLANNER_CATALOG_CHANGED(true);

    private final boolean batchConsumed;

    DomainEventType(boolean batchConsumed) {
        this.batchConsumed = batchConsumed;
    }

    public boolean isBatchConsumed() {
        return batchConsumed;
    }

    public static List<String> dispatchedNames() {
        return Arrays.stream(values()).filter(type -> !type.batchConsumed).map(Enum::name).toList();
    }
}
