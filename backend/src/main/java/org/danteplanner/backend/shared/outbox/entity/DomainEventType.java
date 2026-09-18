package org.danteplanner.backend.shared.outbox.entity;

/**
 * The constant is stored as text in {@code domain_events.event_type}, so a renamed constant
 * orphans every row already written under the old name.
 */
public enum DomainEventType {
    PLANNER_PUBLISHED, PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED
}
