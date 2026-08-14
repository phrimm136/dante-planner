package org.danteplanner.backend.shared.outbox.entity;

/**
 * The kinds of observer effect the outbox carries.
 *
 * <p>The constant is stored as text in {@code domain_events.event_type} and is what selects the
 * effect arm at dispatch time, so a renamed constant orphans every row already written under the
 * old name.</p>
 */
public enum DomainEventType {
    PLANNER_PUBLISHED, PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED
}
