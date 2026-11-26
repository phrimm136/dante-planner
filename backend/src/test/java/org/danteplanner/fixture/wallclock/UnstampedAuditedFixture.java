package org.danteplanner.fixture.wallclock;

import java.time.Instant;

import jakarta.persistence.EntityListeners;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;

@EntityListeners(AuditedTimestampListener.class)
public class UnstampedAuditedFixture {

    private Instant createdAt;
}
