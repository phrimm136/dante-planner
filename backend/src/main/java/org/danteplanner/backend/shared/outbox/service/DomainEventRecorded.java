package org.danteplanner.backend.shared.outbox.service;

/**
 * An event row that committed and can now be dispatched.
 *
 * <p>Carries the id alone: the row is the durable record, and a listener that read anything else
 * from this object would be reading a value the dispatch is about to re-read anyway.</p>
 *
 * @param eventId the recorded event's id
 */
public record DomainEventRecorded(long eventId) {
}
