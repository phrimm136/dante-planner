package org.danteplanner.fixture.wallclock;

import jakarta.persistence.EntityListeners;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;

@EntityListeners(AuditedTimestampListener.class)
public class InheritedStampAuditedFixture extends StampedAuditedBase {
}
